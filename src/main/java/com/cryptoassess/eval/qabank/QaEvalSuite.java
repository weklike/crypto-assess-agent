package com.cryptoassess.eval.qabank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.common.llm.PromptTemplates;
import com.cryptoassess.common.llm.StructuredLlmClient;
import com.cryptoassess.common.llm.StructuredRequest;
import com.cryptoassess.common.llm.StructuredResult;
import com.cryptoassess.eval.EvalContext;
import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.EvalOutcome;
import com.cryptoassess.eval.EvalSuite;
import com.cryptoassess.eval.metrics.Percentiles;
import com.cryptoassess.eval.report.MarkdownTable;
import com.cryptoassess.qa.QaProperties;
import com.cryptoassess.retrieval.HybridSearchService;
import com.cryptoassess.retrieval.RetrievalProperties;
import com.cryptoassess.retrieval.SearchHit;
import com.cryptoassess.retrieval.SearchMode;
import com.cryptoassess.retrieval.SearchRequest;
import com.cryptoassess.retrieval.SearchResponse;
import org.springframework.stereotype.Component;

/**
 * 问答评测（考核题库抽样）：无检索、hybrid、hybrid_rerank 三组，同一模型、温度 0。
 * 题库只发往本地模型（{@link ExamChatModel}），不使用 spring.ai.openai 配置的外部接口；
 * cases.jsonl 只写题号、组别和对错；题目原文、选项、模型输出都不落盘。
 */
@Component
public class QaEvalSuite implements EvalSuite {

	static final String PROMPT_VERSION = "qa-exam.v1";

	static final String SCHEMA = "qa-exam-answer.v1";

	static final int TOP_K = 6;

	static final List<String> GROUPS = List.of("none", "hybrid", "hybrid_rerank");

	static final double[] SWEEP = { 0.0, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8 };

	static final int MANUAL_CHECK_SIZE = 30;

	static final long MANUAL_CHECK_SEED = 20260924L;

	private final HybridSearchService searchService;

	private final StructuredLlmClient llmClient;

	private final QaProperties qaProperties;

	private final RetrievalProperties retrievalProperties;

	private final com.cryptoassess.common.llm.LlmCallMapper llmCallMapper;

	private final ExamChatModel examModel;

	public QaEvalSuite(HybridSearchService searchService, StructuredLlmClient llmClient, QaProperties qaProperties,
			RetrievalProperties retrievalProperties, com.cryptoassess.common.llm.LlmCallMapper llmCallMapper,
			ExamChatModel examModel) {
		this.searchService = searchService;
		this.llmClient = llmClient;
		this.examModel = examModel;
		this.qaProperties = qaProperties;
		this.retrievalProperties = retrievalProperties;
		this.llmCallMapper = llmCallMapper;
	}

	public record ExamAnswer(List<String> answer, List<String> citations) {
	}

	record Case(String id, String type, String group, boolean correct, String status, Double topScore,
			int citationsTotal, int citationsValid, long latencyMs, Integer inputTokens, Integer outputTokens,
			String model, Long llmCallId) {
	}

	@Override
	public String name() {
		return "qa";
	}

	@Override
	public EvalOutcome run(EvalContext context) throws IOException {
		// 读题库之前先确认模型在本地；检查不通过整次评测失败，不换用其他模型
		StructuredLlmClient client = this.llmClient.withModel(this.examModel.chatModel(), this.examModel.modelName());
		Path datasetPath = (context.datasetPath() != null) ? context.datasetPath()
				: Path.of("data/private", "qa_bank_sample." + context.datasetVersion() + ".jsonl");
		List<String> lines = Files.readAllLines(datasetPath, StandardCharsets.UTF_8);
		List<QaBankItem> items = new ArrayList<>();
		for (String line : lines) {
			if (!line.isBlank()) {
				items.add(EvalFiles.JSON.readValue(line, QaBankItem.class));
			}
		}
		List<Case> cases = new ArrayList<>();
		for (QaBankItem item : items) {
			for (String group : GROUPS) {
				cases.add(runCase(client, item, group));
			}
		}
		Instant finishedAt = Instant.now();

		Map<String, Object> config = new LinkedHashMap<>();
		config.put("suite", name());
		config.put("gitCommit", context.gitCommit());
		config.put("datasetVersion", context.datasetVersion());
		config.put("datasetSha256", EvalFiles.sha256(String.join("\n", lines)));
		config.put("sampleCount", items.size());
		config.put("typeCounts", items.stream()
			.collect(Collectors.groupingBy(QaBankItem::type, TreeMap::new, Collectors.counting())));
		config.put("examModel", this.examModel.modelName());
		config.put("examModelEndpoint", this.examModel.endpoint());
		config.put("models", cases.stream().map(Case::model).filter(m -> m != null).distinct().sorted().toList());
		config.put("promptVersion", PROMPT_VERSION);
		config.put("outputSchema", SCHEMA);
		config.put("temperature", 0.0);
		config.put("groups", GROUPS);
		config.put("topK", TOP_K);
		config.put("refuseThreshold", this.retrievalProperties.refuseThreshold());
		config.put("timeoutSeconds", this.qaProperties.timeout().toSeconds());
		config.put("startedAt", context.startedAt().toString());
		config.put("finishedAt", finishedAt.toString());
		config.put("durationSeconds", Duration.between(context.startedAt(), finishedAt).toSeconds());

		Map<String, Object> metrics = new LinkedHashMap<>();
		for (String group : GROUPS) {
			metrics.put(group, aggregate(cases.stream().filter(c -> c.group().equals(group)).toList()));
		}
		List<Case> reranked = cases.stream().filter(c -> c.group().equals("hybrid_rerank")).toList();
		metrics.put("thresholdSweep", sweep(reranked));

		EvalFiles.writeCases(context.outputDir(), cases.stream().map(c -> {
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("id", c.id());
			line.put("group", c.group());
			line.put("correct", c.correct());
			return line;
		}).toList());
		EvalFiles.writeMetrics(context.outputDir(), config, metrics);
		EvalFiles.writeReport(context.outputDir(), report(config, metrics, items));
		return new EvalOutcome(config, metrics);
	}

	private Case runCase(StructuredLlmClient client, QaBankItem item, String group) {
		long start = System.nanoTime();
		String context = "本题不提供参考条款，请依据你已有的知识作答。";
		List<String> retrieved = List.of();
		Double topScore = null;
		if (!group.equals("none")) {
			SearchMode mode = SearchMode.fromValue(group);
			SearchResponse search = this.searchService
				.search(new SearchRequest(query(item), mode, TOP_K, null, null));
			retrieved = search.hits().stream().map(SearchHit::clauseRef).toList();
			if (!search.hits().isEmpty() && mode == SearchMode.HYBRID_RERANK) {
				topScore = search.hits().get(0).rerankScore();
			}
			context = "参考条款（作答依据，引用时写方括号里的内容）：\n" + clauses(search.hits());
		}
		String prompt = PromptTemplates.render(PROMPT_VERSION, Map.of("typeName", typeName(item.type()), "context",
				context, "stem", item.stem(), "options", options(item)));
		try {
			StructuredResult<ExamAnswer> result = client.call(new StructuredRequest("exam", PROMPT_VERSION,
					prompt, SCHEMA, 0.0, this.qaProperties.timeout()), ExamAnswer.class);
			Set<String> predicted = new HashSet<>(result.value().answer());
			boolean correct = predicted.equals(new HashSet<>(item.answer()));
			List<String> cited = result.value().citations();
			Set<String> allowed = new HashSet<>(retrieved);
			int valid = (int) cited.stream().distinct().filter(allowed::contains).count();
			return new Case(item.id(), item.type(), group, correct, "ok", topScore, (int) cited.stream().distinct().count(),
					valid, millisSince(start), result.inputTokens(), result.outputTokens(), result.model(),
					result.llmCallId());
		}
		catch (AppException ex) {
			// 模型服务不可用时整次评测失败，免得把服务故障算成答错
			if (ex.type() != ErrorType.LLM_TIMEOUT && ex.type() != ErrorType.LLM_INVALID_OUTPUT) {
				throw ex;
			}
			// 输出不合规、超时按答错计，并单独统计次数；不重试
			String status = (ex.type() == ErrorType.LLM_TIMEOUT) ? "timeout" : "invalid_output";
			return new Case(item.id(), item.type(), group, false, status, topScore, 0, 0, millisSince(start), null,
					null, null, null);
		}
	}

	static String query(QaBankItem item) {
		return item.stem() + " " + String.join(" ", item.options().values());
	}

	private static String clauses(List<SearchHit> hits) {
		StringBuilder out = new StringBuilder();
		for (SearchHit hit : hits) {
			out.append('[').append(hit.clauseRef()).append("] ").append(hit.title()).append('\n')
				.append(hit.snippet()).append("\n\n");
		}
		return out.toString().strip();
	}

	private static String options(QaBankItem item) {
		return new TreeMap<>(item.options()).entrySet()
			.stream()
			.map(e -> e.getKey() + ". " + e.getValue())
			.collect(Collectors.joining("\n"));
	}

	private static String typeName(String type) {
		return switch (type) {
			case "multi" -> "多选题";
			case "judge" -> "判断题（A 表示正确，B 表示错误）";
			default -> "单选题";
		};
	}

	private Map<String, Object> aggregate(List<Case> cases) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("n", cases.size());
		m.put("accuracy", rate(cases.stream().filter(Case::correct).count(), cases.size()));
		Map<String, Object> byType = new TreeMap<>();
		cases.stream().collect(Collectors.groupingBy(Case::type)).forEach((type, group) -> byType.put(type,
				Map.of("n", group.size(), "accuracy", rate(group.stream().filter(Case::correct).count(), group.size()))));
		m.put("byType", byType);
		m.put("statusCounts", cases.stream().collect(Collectors.groupingBy(Case::status, TreeMap::new, Collectors.counting())));
		long cited = cases.stream().mapToLong(Case::citationsTotal).sum();
		long valid = cases.stream().mapToLong(Case::citationsValid).sum();
		m.put("citationsTotal", cited);
		m.put("citationsValid", valid);
		m.put("citationValidRate", (cited == 0) ? null : rate(valid, cited));
		long wouldRefuse = cases.stream()
			.filter(c -> c.topScore() != null && c.topScore() < this.retrievalProperties.refuseThreshold())
			.count();
		m.put("refusalRate", cases.stream().anyMatch(c -> c.topScore() != null) ? rate(wouldRefuse, cases.size()) : null);
		List<Long> latencies = cases.stream().map(Case::latencyMs).toList();
		m.put("p50Ms", Percentiles.nearestRank(latencies, 50));
		m.put("p95Ms", Percentiles.nearestRank(latencies, 95));
		m.put("inputTokens", cases.stream().filter(c -> c.inputTokens() != null).mapToLong(Case::inputTokens).sum());
		m.put("outputTokens", cases.stream().filter(c -> c.outputTokens() != null).mapToLong(Case::outputTokens).sum());
		List<Long> ids = cases.stream().map(Case::llmCallId).filter(java.util.Objects::nonNull).toList();
		Map<String, Object> usage = ids.isEmpty() ? Map.of() : this.llmCallMapper.summarizeIds(ids);
		m.put("costCny", usage.get("costCny"));
		m.put("unpricedCalls", usage.getOrDefault("unpriced", 0));
		return m;
	}

	/** 拒答阈值扫描：每个阈值下会被拒答的比例，以及保留题与拒答题各自的准确率，供校准阈值。 */
	private static List<Map<String, Object>> sweep(List<Case> cases) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (double threshold : SWEEP) {
			List<Case> refused = cases.stream().filter(c -> c.topScore() == null || c.topScore() < threshold).toList();
			List<Case> kept = cases.stream().filter(c -> c.topScore() != null && c.topScore() >= threshold).toList();
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("threshold", threshold);
			row.put("refusalRate", rate(refused.size(), cases.size()));
			row.put("keptAccuracy", kept.isEmpty() ? null : rate(kept.stream().filter(Case::correct).count(), kept.size()));
			row.put("refusedAccuracy",
					refused.isEmpty() ? null : rate(refused.stream().filter(Case::correct).count(), refused.size()));
			rows.add(row);
		}
		return rows;
	}

	@SuppressWarnings("unchecked")
	private String report(Map<String, Object> config, Map<String, Object> metrics, List<QaBankItem> items) {
		StringBuilder md = new StringBuilder("# 问答评测报告（考核题库抽样）\n\n");
		md.append("题目原文不进入仓库；本报告与 cases.jsonl 只含题号、对错和聚合指标。\n\n## 运行信息\n\n");
		MarkdownTable info = new MarkdownTable("项", "值");
		config.forEach(info::row);
		md.append(info.render()).append('\n');

		md.append("## 三组对比\n\n");
		MarkdownTable table = new MarkdownTable("组", "n", "准确率", "引用有效率", "拒答率", "P50 ms", "P95 ms", "输入 token",
				"输出 token", "费用（元）", "状态");
		for (String group : GROUPS) {
			Map<String, Object> g = (Map<String, Object>) metrics.get(group);
			table.row(group, g.get("n"), g.get("accuracy"), g.get("citationValidRate"), g.get("refusalRate"),
					g.get("p50Ms"), g.get("p95Ms"), g.get("inputTokens"), g.get("outputTokens"),
					(g.get("costCny") == null) ? "未配置单价" : g.get("costCny"), g.get("statusCounts"));
		}
		md.append(table.render()).append('\n');
		md.append("准确率：多选题要求所选集合与答案完全一致；输出不合规或超时按答错计。引用有效率 = 引用在本题检索结果中的比例（无检索组不提供条款）。")
			.append("拒答率 = 最高重排分低于当前阈值 ").append(config.get("refuseThreshold"))
			.append(" 的比例；评测时这些题仍然作答，以便下面的阈值扫描。\n\n");

		md.append("## 按题型的准确率\n\n");
		MarkdownTable byType = new MarkdownTable("题型", "none", "hybrid", "hybrid_rerank");
		for (String type : QaBankItem.TYPES) {
			List<Object> row = new ArrayList<>(List.of(type));
			for (String group : GROUPS) {
				Map<String, Object> t = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) metrics
					.get(group)).get("byType")).get(type);
				row.add((t == null) ? null : t.get("accuracy") + "（n=" + t.get("n") + "）");
			}
			byType.row(row.toArray());
		}
		md.append(byType.render()).append('\n');

		md.append("## 拒答阈值扫描（hybrid_rerank）\n\n");
		MarkdownTable sweep = new MarkdownTable("阈值", "拒答率", "保留题准确率", "拒答题准确率");
		for (Map<String, Object> row : (List<Map<String, Object>>) metrics.get("thresholdSweep")) {
			sweep.row(String.format(Locale.ROOT, "%.2f", (Double) row.get("threshold")), row.get("refusalRate"),
					row.get("keptAccuracy"), row.get("refusedAccuracy"));
		}
		md.append(sweep.render()).append('\n');
		md.append("选阈值时看两件事：拒答题准确率应明显低于保留题，拒答率不能高到影响可用性。结论由仓库所有者写入 docs/decisions.md。\n\n");

		md.append("## 人工核对（30 题，待仓库所有者填写）\n\n");
		md.append("从样本中用固定种子 ").append(MANUAL_CHECK_SEED)
			.append(" 抽取，核对 hybrid_rerank 组的引用是否真正支撑答案。\n\n");
		List<String> ids = new ArrayList<>(items.stream().map(QaBankItem::id).sorted().toList());
		Collections.shuffle(ids, new Random(MANUAL_CHECK_SEED));
		MarkdownTable manual = new MarkdownTable("题号", "引用支撑答案（是/部分/否）", "备注");
		ids.stream().limit(MANUAL_CHECK_SIZE).sorted().forEach(id -> manual.row(id, "", ""));
		md.append(manual.render());
		return md.toString();
	}

	private static double rate(long part, long total) {
		return (total == 0) ? 0 : (double) part / total;
	}

	private static long millisSince(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

}
