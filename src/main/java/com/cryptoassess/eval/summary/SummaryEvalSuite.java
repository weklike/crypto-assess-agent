package com.cryptoassess.eval.summary;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cryptoassess.eval.EvalContext;
import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.EvalOutcome;
import com.cryptoassess.eval.EvalRunResult;
import com.cryptoassess.eval.EvalService;
import com.cryptoassess.eval.EvalSuite;
import com.cryptoassess.eval.report.MarkdownTable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 全量评测：在同一个 git commit、同一组模型/提示词/数据集版本下依次跑 retrieval、qa、gap、scoring，
 * 每个子评测照常写自己的结果目录；总报告链接各子报告，失败项如实列出，不改任何数据集、阈值和提示词。
 */
@Component
public class SummaryEvalSuite implements EvalSuite {

	static final List<String> SUITES = List.of("retrieval", "qa", "gap", "scoring");

	@FunctionalInterface
	public interface Runner {

		EvalRunResult run(String suite) throws IOException;

	}

	private final Runner runner;

	private final List<String> suites;

	@Autowired
	public SummaryEvalSuite(ObjectProvider<EvalService> evalService) {
		this(suite -> evalService.getObject().run(suite), SUITES);
	}

	public SummaryEvalSuite(Runner runner, List<String> suites) {
		this.runner = runner;
		this.suites = suites;
	}

	@Override
	public String name() {
		return "summary";
	}

	@Override
	public EvalOutcome run(EvalContext context) throws IOException {
		List<EvalRunResult> results = new ArrayList<>();
		for (String suite : this.suites) {
			results.add(this.runner.run(suite));
		}
		Instant finishedAt = Instant.now();
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("suite", name());
		config.put("gitCommit", context.gitCommit());
		config.put("datasetVersion", context.datasetVersion());
		config.put("suites", this.suites);
		config.put("startedAt", context.startedAt().toString());
		config.put("finishedAt", finishedAt.toString());
		config.put("durationSeconds", Duration.between(context.startedAt(), finishedAt).toSeconds());

		Map<String, Object> perSuite = new LinkedHashMap<>();
		int completed = 0;
		int failed = 0;
		for (EvalRunResult r : results) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("completed", r.completed());
			entry.put("outputDir", r.outputDir().getFileName().toString());
			entry.put("error", r.error());
			if (r.completed()) {
				completed++;
				entry.put("gitCommit", r.outcome().config().get("gitCommit"));
				entry.put("metrics", r.outcome().metrics());
			}
			else {
				failed++;
			}
			perSuite.put(r.suite(), entry);
		}
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("completed", completed);
		metrics.put("failed", failed);
		metrics.put("suites", perSuite);

		EvalFiles.writeMetrics(context.outputDir(), config, metrics);
		EvalFiles.writeCases(context.outputDir(), results.stream().map(r -> {
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("suite", r.suite());
			line.put("completed", r.completed());
			line.put("output_dir", r.outputDir().getFileName().toString());
			return line;
		}).toList());
		EvalFiles.writeReport(context.outputDir(), report(context, config, results));
		return new EvalOutcome(config, metrics);
	}

	private static String report(EvalContext context, Map<String, Object> config, List<EvalRunResult> results) {
		StringBuilder md = new StringBuilder("# 全量评测总报告\n\n## 运行信息\n\n");
		MarkdownTable info = new MarkdownTable("项", "值");
		config.forEach(info::row);
		md.append(info.render()).append('\n');
		md.append("## 各项评测\n\n");
		MarkdownTable table = new MarkdownTable("评测", "状态", "关键指标", "git commit", "子报告");
		for (EvalRunResult r : results) {
			String link = "[" + r.outputDir().getFileName() + "](../" + r.outputDir().getFileName() + "/report.md)";
			if (!r.completed()) {
				table.row(r.suite(), "失败：" + r.error(), "—", "—", link);
				continue;
			}
			Object commit = r.outcome().config().get("gitCommit");
			String same = Objects.equals(commit, context.gitCommit()) ? String.valueOf(commit)
					: commit + "（与总报告不一致）";
			table.row(r.suite(), "完成", headline(r.suite(), r.outcome().metrics()), same, link);
		}
		md.append(table.render()).append('\n');
		md.append("每个数字的来源是对应子报告的 metrics.json；失败的子评测保留了目录和 error.txt。\n");
		return md.toString();
	}

	/** 各评测的关键指标，缺字段时给出“见子报告”，不因结构变化而失败。 */
	@SuppressWarnings("unchecked")
	static String headline(String suite, Map<String, Object> metrics) {
		try {
			return switch (suite) {
				case "retrieval" -> {
					List<String> parts = new ArrayList<>();
					for (String mode : List.of("bm25", "dense", "hybrid", "hybrid_rerank")) {
						Map<String, Object> m = (Map<String, Object>) metrics.get(mode);
						parts.add(mode + " R@5=" + fmt(m.get("recall@5")) + " MRR=" + fmt(m.get("mrr@10")));
					}
					yield String.join("；", parts);
				}
				case "qa" -> {
					List<String> parts = new ArrayList<>();
					for (String group : List.of("none", "hybrid", "hybrid_rerank")) {
						Map<String, Object> m = (Map<String, Object>) metrics.get(group);
						parts.add(group + " 准确率=" + fmt(m.get("accuracy")));
					}
					yield String.join("；", parts);
				}
				case "gap" -> {
					List<String> parts = new ArrayList<>();
					for (String approach : List.of("plain_prompt", "workflow")) {
						Map<String, Object> m = (Map<String, Object>) metrics.get(approach);
						parts.add(approach + " 召回=" + fmt(m.get("recall")) + " 误报=" + fmt(m.get("falsePositiveRate")));
					}
					yield String.join("；", parts);
				}
				case "scoring" -> "一致 " + metrics.get("agreed") + " / " + metrics.get("cases");
				default -> "见子报告";
			};
		}
		catch (RuntimeException ex) {
			return "见子报告";
		}
	}

	private static String fmt(Object value) {
		return (value instanceof Number n) ? String.format(java.util.Locale.ROOT, "%.3f", n.doubleValue()) : "—";
	}

}
