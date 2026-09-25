package com.cryptoassess.eval.retrieval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cryptoassess.eval.EvalContext;
import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.EvalOutcome;
import com.cryptoassess.eval.EvalProperties;
import com.cryptoassess.eval.EvalSuite;
import com.cryptoassess.eval.dataset.DatasetIssue;
import com.cryptoassess.eval.dataset.RetrievalDatasetValidator;
import com.cryptoassess.eval.dataset.RetrievalQuery;
import com.cryptoassess.eval.metrics.Percentiles;
import com.cryptoassess.eval.metrics.RetrievalMetrics;
import com.cryptoassess.eval.report.MarkdownTable;
import com.cryptoassess.knowledge.EmbeddingClient;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.retrieval.HybridSearchService;
import com.cryptoassess.retrieval.RerankProperties;
import com.cryptoassess.retrieval.RetrievalProperties;
import com.cryptoassess.retrieval.SearchHit;
import com.cryptoassess.retrieval.SearchMode;
import com.cryptoassess.retrieval.SearchRequest;
import com.cryptoassess.retrieval.SearchResponse;
import org.springframework.stereotype.Component;

/**
 * 检索评测：四种模式各跑一遍评测集，输出 Recall@k、MRR@10、nDCG@10 与延迟，并按问法和安全层面分组。
 * 检索时不加过滤条件（评测的是纯检索能力），layer 只用于分组统计。
 */
@Component
public class RetrievalEvalSuite implements EvalSuite {

	static final int EVAL_K = 10;

	static final int[] RECALL_KS = { 1, 3, 5, 10 };

	private final HybridSearchService searchService;

	private final KbClauseMapper clauseMapper;

	private final ClauseIndexer clauseIndexer;

	private final EmbeddingClient embeddingClient;

	private final RetrievalProperties retrievalProperties;

	private final RerankProperties rerankProperties;

	private final EvalProperties evalProperties;

	public RetrievalEvalSuite(HybridSearchService searchService, KbClauseMapper clauseMapper,
			ClauseIndexer clauseIndexer, EmbeddingClient embeddingClient, RetrievalProperties retrievalProperties,
			RerankProperties rerankProperties, EvalProperties evalProperties) {
		this.searchService = searchService;
		this.clauseMapper = clauseMapper;
		this.clauseIndexer = clauseIndexer;
		this.embeddingClient = embeddingClient;
		this.retrievalProperties = retrievalProperties;
		this.rerankProperties = rerankProperties;
		this.evalProperties = evalProperties;
	}

	@Override
	public String name() {
		return "retrieval";
	}

	@Override
	public EvalOutcome run(EvalContext context) throws IOException {
		Path datasetPath = (context.datasetPath() != null) ? context.datasetPath()
				: Path.of(this.evalProperties.datasetsDir(), "retrieval_queries." + context.datasetVersion() + ".jsonl");
		List<String> lines = Files.readAllLines(datasetPath, StandardCharsets.UTF_8);
		RetrievalDatasetValidator.Report validation = validate(lines);
		if (!validation.valid()) {
			throw new IllegalStateException("dataset " + datasetPath + " is invalid: "
					+ validation.issues().stream().limit(10).map(DatasetIssue::toString).toList());
		}
		var cacheBefore = this.embeddingClient.cacheStats();
		List<CaseResult> cases = new ArrayList<>();
		for (SearchMode mode : SearchMode.values()) {
			for (RetrievalQuery query : validation.queries()) {
				cases.add(runCase(mode, query));
			}
		}
		Instant finishedAt = Instant.now();

		Map<String, Object> config = config(context, datasetPath, lines, validation, finishedAt);
		config.put("embeddingCache", cacheBefore.map(before -> {
			EmbeddingClient.CacheStats after = this.embeddingClient.cacheStats().orElseThrow();
			long hits = after.hits() - before.hits();
			long misses = after.misses() - before.misses();
			return (Object) Map.of("hits", hits, "misses", misses, "hitRate",
					(hits + misses == 0) ? 0.0 : (double) hits / (hits + misses));
		}).orElse("disabled"));
		Map<String, Object> metrics = new LinkedHashMap<>();
		for (SearchMode mode : SearchMode.values()) {
			List<CaseResult> modeCases = cases.stream().filter(c -> c.mode() == mode).toList();
			Map<String, Object> modeMetrics = new LinkedHashMap<>(aggregate(modeCases));
			modeMetrics.put("byStyle", groupBy(modeCases, CaseResult::style));
			modeMetrics.put("byLayer", groupBy(modeCases, c -> (c.layer() == null) ? "未标注" : c.layer()));
			metrics.put(mode.value(), modeMetrics);
		}
		EvalFiles.writeCases(context.outputDir(), cases.stream().map(CaseResult::toJson).toList());
		EvalFiles.writeMetrics(context.outputDir(), config, metrics);
		EvalFiles.writeReport(context.outputDir(), report(config, metrics, cases));
		return new EvalOutcome(config, metrics);
	}

	/** 先解析一遍收集全部引用，一次查库确认存在性，再正式校验。 */
	private RetrievalDatasetValidator.Report validate(List<String> lines) {
		Set<String> refs = new HashSet<>();
		new RetrievalDatasetValidator(ref -> refs.add(ref) || true).validate(lines);
		Set<String> existing = refs.isEmpty() ? Set.of()
				: this.clauseMapper.findByRefs(List.copyOf(refs))
					.stream()
					.map(KbClause::clauseRef)
					.collect(Collectors.toSet());
		return new RetrievalDatasetValidator(existing::contains).validate(lines);
	}

	private CaseResult runCase(SearchMode mode, RetrievalQuery query) {
		long start = System.nanoTime();
		SearchResponse response = this.searchService
			.search(new SearchRequest(query.query(), mode, EVAL_K, null, null));
		long latencyMs = (System.nanoTime() - start) / 1_000_000;
		List<String> ranked = response.hits().stream().map(SearchHit::clauseRef).toList();
		Set<String> gold = Set.copyOf(query.goldRefs());
		Map<String, Double> recall = new LinkedHashMap<>();
		for (int k : RECALL_KS) {
			recall.put("recall@" + k, RetrievalMetrics.recallAt(ranked, gold, k));
		}
		Integer firstHit = null;
		for (int i = 0; i < ranked.size() && firstHit == null; i++) {
			if (gold.contains(ranked.get(i))) {
				firstHit = i + 1;
			}
		}
		return new CaseResult(query.id(), mode, query.style(), query.layer(), query.goldRefs(), ranked, firstHit,
				recall, RetrievalMetrics.reciprocalRank(ranked, gold, EVAL_K),
				RetrievalMetrics.ndcgAt(ranked, gold, EVAL_K), latencyMs);
	}

	static Map<String, Object> aggregate(List<CaseResult> cases) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("n", cases.size());
		for (int k : RECALL_KS) {
			metrics.put("recall@" + k, mean(cases, c -> c.recall().get("recall@" + k)));
		}
		metrics.put("mrr@10", mean(cases, CaseResult::reciprocalRank));
		metrics.put("ndcg@10", mean(cases, CaseResult::ndcg));
		List<Long> latencies = cases.stream().map(CaseResult::latencyMs).toList();
		metrics.put("p50Ms", Percentiles.nearestRank(latencies, 50));
		metrics.put("p95Ms", Percentiles.nearestRank(latencies, 95));
		return metrics;
	}

	private static Map<String, Object> groupBy(List<CaseResult> cases, Function<CaseResult, String> key) {
		Map<String, List<CaseResult>> groups = cases.stream()
			.collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
		Map<String, Object> result = new LinkedHashMap<>();
		groups.forEach((name, group) -> result.put(name, aggregate(group)));
		return result;
	}

	private static double mean(List<CaseResult> cases, Function<CaseResult, Double> metric) {
		return cases.stream().mapToDouble(metric::apply).average().orElse(0);
	}

	private Map<String, Object> config(EvalContext context, Path datasetPath, List<String> lines,
			RetrievalDatasetValidator.Report validation, Instant finishedAt) {
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("suite", name());
		config.put("gitCommit", context.gitCommit());
		config.put("datasetVersion", context.datasetVersion());
		config.put("datasetPath", datasetPath.toString());
		config.put("datasetSha256", EvalFiles.sha256(String.join("\n", lines)));
		config.put("sampleCount", validation.queries().size());
		config.put("styleCounts", validation.styleCounts());
		config.put("index", this.clauseIndexer.currentIndex().orElse("unknown"));
		config.put("embeddingModel", this.embeddingClient.model());
		config.put("rerankModel", this.rerankProperties.model());
		config.put("rerankTopN", this.rerankProperties.topN());
		config.put("rrfK", this.retrievalProperties.rrfK());
		config.put("candidatesPerRetriever", this.retrievalProperties.candidates());
		config.put("knnNumCandidates", this.retrievalProperties.numCandidates());
		config.put("evalK", EVAL_K);
		config.put("modes", List.of(SearchMode.values()).stream().map(SearchMode::value).toList());
		config.put("startedAt", context.startedAt().toString());
		config.put("finishedAt", finishedAt.toString());
		config.put("durationSeconds", Duration.between(context.startedAt(), finishedAt).toSeconds());
		return config;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> sub(Map<String, Object> map, String key) {
		return (Map<String, Object>) map.get(key);
	}

	@SuppressWarnings("unchecked")
	private String report(Map<String, Object> config, Map<String, Object> metrics, List<CaseResult> cases) {
		StringBuilder md = new StringBuilder("# 检索评测报告\n\n## 运行信息\n\n");
		MarkdownTable info = new MarkdownTable("项", "值");
		config.forEach(info::row);
		md.append(info.render()).append('\n');
		Map<String, Long> styles = (Map<String, Long>) config.get("styleCounts");
		long total = styles.values().stream().mapToLong(Long::longValue).sum();
		md.append("问法构成：")
			.append(styles.entrySet()
				.stream()
				.map(e -> e.getKey() + " " + e.getValue() + " 条（" + MarkdownTable.percent(e.getValue(), total) + "）")
				.collect(Collectors.joining("，")))
			.append("。改写类查询偏向向量检索，比较各模式时注意构成比例。\n\n");

		md.append("## 总表\n\n");
		MarkdownTable overall = new MarkdownTable("模式", "Recall@1", "Recall@3", "Recall@5", "Recall@10", "MRR@10",
				"nDCG@10", "P50 ms", "P95 ms");
		for (SearchMode mode : SearchMode.values()) {
			Map<String, Object> m = sub(metrics, mode.value());
			overall.row(mode.value(), m.get("recall@1"), m.get("recall@3"), m.get("recall@5"), m.get("recall@10"),
					m.get("mrr@10"), m.get("ndcg@10"), m.get("p50Ms"), m.get("p95Ms"));
		}
		md.append(overall.render()).append('\n');
		md.append("延迟是评测进程内调用检索服务的端到端耗时：dense、hybrid 含查询向量化，hybrid_rerank 另含重排。\n\n");

		appendGroupTable(md, metrics, "byStyle", "## 按问法分组（Recall@5 / MRR@10）");
		appendGroupTable(md, metrics, "byLayer", "## 按安全层面分组（Recall@5 / MRR@10）");

		md.append("## 最差 10 条（按四种模式的平均 MRR@10 升序）\n\n");
		Map<String, List<CaseResult>> byQuery = cases.stream()
			.collect(Collectors.groupingBy(CaseResult::id, LinkedHashMap::new, Collectors.toList()));
		MarkdownTable worst = new MarkdownTable("id", "问法", "平均 MRR@10", "gold_refs", "hybrid_rerank 前 3");
		byQuery.entrySet()
			.stream()
			.sorted(Comparator.comparingDouble((Map.Entry<String, List<CaseResult>> e) -> averageRr(e.getValue()))
				.thenComparing(Map.Entry::getKey))
			.limit(10)
			.forEach(e -> {
				CaseResult rerank = e.getValue()
					.stream()
					.filter(c -> c.mode() == SearchMode.HYBRID_RERANK)
					.findFirst()
					.orElse(e.getValue().get(0));
				worst.row(e.getKey(), rerank.style(), averageRr(e.getValue()), String.join(", ", rerank.goldRefs()),
						String.join(", ", rerank.ranked().subList(0, Math.min(3, rerank.ranked().size()))));
			});
		md.append(worst.render());
		return md.toString();
	}

	private static double averageRr(List<CaseResult> cases) {
		return cases.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0);
	}

	private static void appendGroupTable(StringBuilder md, Map<String, Object> metrics, String group, String title) {
		md.append(title).append("\n\n");
		Set<String> keys = new TreeSet<>();
		for (SearchMode mode : SearchMode.values()) {
			keys.addAll(sub(sub(metrics, mode.value()), group).keySet());
		}
		List<String> header = new ArrayList<>(List.of("分组", "n"));
		for (SearchMode mode : SearchMode.values()) {
			header.add(mode.value());
		}
		MarkdownTable table = new MarkdownTable(header.toArray(String[]::new));
		for (String key : keys) {
			List<Object> row = new ArrayList<>(List.of(key));
			Object n = null;
			for (SearchMode mode : SearchMode.values()) {
				Map<String, Object> g = sub(sub(sub(metrics, mode.value()), group), key);
				if (g == null) {
					row.add(null);
					continue;
				}
				n = g.get("n");
				row.add(String.format(Locale.ROOT, "%.3f / %.3f", (Double) g.get("recall@5"), (Double) g.get("mrr@10")));
			}
			row.add(1, n);
			table.row(row.toArray());
		}
		md.append(table.render()).append('\n');
	}

	record CaseResult(String id, SearchMode mode, String style, String layer, List<String> goldRefs, List<String> ranked,
			Integer firstHitRank, Map<String, Double> recall, double reciprocalRank, double ndcg, long latencyMs) {

		Map<String, Object> toJson() {
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("id", this.id);
			line.put("mode", this.mode.value());
			line.put("style", this.style);
			line.put("layer", this.layer);
			line.put("gold_refs", this.goldRefs);
			line.put("ranked", this.ranked);
			line.put("first_hit_rank", this.firstHitRank);
			line.putAll(this.recall);
			line.put("rr", this.reciprocalRank);
			line.put("ndcg@10", this.ndcg);
			line.put("latency_ms", this.latencyMs);
			return line;
		}

	}

}
