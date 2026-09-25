package com.cryptoassess.eval.gap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.cryptoassess.assessment.AnalysisRunner;
import com.cryptoassess.assessment.AssessFinding;
import com.cryptoassess.assessment.AssessFindingMapper;
import com.cryptoassess.assessment.AssessObject;
import com.cryptoassess.assessment.AssessObjectMapper;
import com.cryptoassess.assessment.AssessProject;
import com.cryptoassess.assessment.AssessmentRequests;
import com.cryptoassess.assessment.AssessmentService;
import com.cryptoassess.assessment.AssessmentStatus;
import com.cryptoassess.assessment.CryptoMeasures;
import com.cryptoassess.assessment.JudgmentProperties;
import com.cryptoassess.assessment.JudgmentService;
import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.llm.LlmCallMapper;
import com.cryptoassess.common.llm.PromptTemplates;
import com.cryptoassess.common.llm.StructuredLlmClient;
import com.cryptoassess.common.llm.StructuredRequest;
import com.cryptoassess.common.llm.StructuredResult;
import com.cryptoassess.eval.EvalContext;
import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.EvalOutcome;
import com.cryptoassess.eval.EvalSuite;
import com.cryptoassess.eval.report.MarkdownTable;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 差距识别评测：方案 A 纯提示词（系统描述 + 适用指标一次交给模型），方案 B 完整工作流（规则检查 + 逐条结构化判定）。
 * 两种方案用同一模型、同一温度。按（测评对象, clause_ref, 判定）严格匹配预期差距项；误报列出来由仓库所有者复核。
 */
@Component
public class GapEvalSuite implements EvalSuite {

	/** 项目名称里的时间用北京时间 24 小时制，页面上直接可读。 */
	private static final java.time.format.DateTimeFormatter BEIJING = java.time.format.DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(java.time.ZoneId.of("Asia/Shanghai"));


	static final String PLAIN_PROMPT = "gap-plain.v1";

	static final String PLAIN_SCHEMA = "gap-plain-output.v1";

	static final List<String> APPROACHES = List.of("plain_prompt", "workflow");

	private final StructuredLlmClient llmClient;

	private final AssessmentService assessmentService;

	private final AnalysisRunner analysisRunner;

	private final AssessObjectMapper objectMapper;

	private final AssessFindingMapper findingMapper;

	private final KbClauseMapper clauseMapper;

	private final LlmCallMapper llmCallMapper;

	private final JudgmentProperties judgmentProperties;

	public GapEvalSuite(StructuredLlmClient llmClient, AssessmentService assessmentService,
			AnalysisRunner analysisRunner, AssessObjectMapper objectMapper, AssessFindingMapper findingMapper,
			KbClauseMapper clauseMapper, LlmCallMapper llmCallMapper, JudgmentProperties judgmentProperties) {
		this.llmClient = llmClient;
		this.assessmentService = assessmentService;
		this.analysisRunner = analysisRunner;
		this.objectMapper = objectMapper;
		this.findingMapper = findingMapper;
		this.clauseMapper = clauseMapper;
		this.llmCallMapper = llmCallMapper;
		this.judgmentProperties = judgmentProperties;
	}

	record Gap(String object, String clauseRef, String judgment) {
	}

	record PlainOutput(List<PlainGap> gaps) {
	}

	record PlainGap(String object, @JsonProperty("clause_ref") String clauseRef, String judgment, String reason) {
	}

	record Run(String system, String approach, String status, Set<Gap> actual, long latencyMs, long inputTokens,
			long outputTokens, int calls, java.math.BigDecimal costCny) {
	}

	@Override
	public String name() {
		return "gap";
	}

	@Override
	public EvalOutcome run(EvalContext context) throws IOException {
		Path dir = (context.datasetPath() != null) ? context.datasetPath() : Path.of("eval/datasets/systems");
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.filter(p -> p.getFileName().toString().endsWith("." + context.datasetVersion() + ".yaml"))
				.sorted()
				.toList();
		}
		if (files.isEmpty()) {
			throw new IllegalStateException("no simulated systems *." + context.datasetVersion() + ".yaml in " + dir);
		}
		List<SimulatedSystem> systems = new ArrayList<>();
		StringBuilder datasetText = new StringBuilder();
		for (Path file : files) {
			systems.add(SimulatedSystem.load(file));
			datasetText.append(Files.readString(file, StandardCharsets.UTF_8));
		}

		List<Run> runs = new ArrayList<>();
		for (SimulatedSystem system : systems) {
			runs.add(plain(system));
			runs.add(workflow(system, context));
		}
		Instant finishedAt = Instant.now();

		Map<String, Object> config = new LinkedHashMap<>();
		config.put("suite", name());
		config.put("gitCommit", context.gitCommit());
		config.put("datasetVersion", context.datasetVersion());
		config.put("datasetSha256", EvalFiles.sha256(datasetText.toString()));
		config.put("systems", systems.size());
		config.put("powerSystems", systems.stream().filter(s -> s.scenario().contains("电力")).count());
		config.put("expectedGaps", systems.stream().mapToInt(s -> s.expected().size()).sum());
		config.put("plainPromptVersion", PLAIN_PROMPT);
		config.put("workflowPromptVersion", JudgmentService.PROMPT_VERSION);
		config.put("temperature", this.judgmentProperties.temperature());
		config.put("matching", "strict (object, clause_ref, judgment)");
		config.put("startedAt", context.startedAt().toString());
		config.put("finishedAt", finishedAt.toString());
		config.put("durationSeconds", Duration.between(context.startedAt(), finishedAt).toSeconds());

		Map<String, Object> metrics = new LinkedHashMap<>();
		for (String approach : APPROACHES) {
			metrics.put(approach, aggregate(systems, runs.stream().filter(r -> r.approach().equals(approach)).toList()));
		}
		List<Map<String, Object>> cases = new ArrayList<>();
		for (Run run : runs) {
			SimulatedSystem system = systems.stream().filter(s -> s.id().equals(run.system())).findFirst().orElseThrow();
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("system", run.system());
			line.put("approach", run.approach());
			line.put("status", run.status());
			line.put("expected", system.expected().size());
			line.put("matched", matched(system, run).size());
			line.put("missed", missed(system, run).stream().map(GapEvalSuite::label).toList());
			line.put("false_positives", falsePositives(system, run).stream().map(GapEvalSuite::label).toList());
			line.put("latency_ms", run.latencyMs());
			line.put("input_tokens", run.inputTokens());
			line.put("output_tokens", run.outputTokens());
			line.put("calls", run.calls());
			cases.add(line);
		}
		EvalFiles.writeCases(context.outputDir(), cases);
		EvalFiles.writeMetrics(context.outputDir(), config, metrics);
		EvalFiles.writeReport(context.outputDir(), report(config, metrics, systems, runs));
		return new EvalOutcome(config, metrics);
	}

	private Run plain(SimulatedSystem system) {
		long start = System.nanoTime();
		Map<String, KbClause> clauses = new LinkedHashMap<>();
		StringBuilder objects = new StringBuilder();
		for (SimulatedSystem.SimObject o : system.objects()) {
			this.clauseMapper.findRequirements(o.layer(), system.level()).forEach(c -> clauses.putIfAbsent(c.clauseRef(), c));
			objects.append('[').append(o.key()).append("] ").append(o.name()).append("（").append(o.layer()).append("）")
				.append(o.description() == null ? "" : "：" + o.description()).append('\n')
				.append(JudgmentService.formatMeasuresPublic(measures(o))).append("\n\n");
		}
		StringBuilder clauseText = new StringBuilder();
		clauses.values().forEach(c -> clauseText.append('[').append(c.clauseRef()).append("] ").append(c.clauseNo())
			.append(' ').append(c.title()).append("（").append(c.layer()).append("）\n").append(c.body()).append("\n\n"));
		String prompt = PromptTemplates.render(PLAIN_PROMPT, Map.of("systemName", system.name(), "level", system.level(),
				"description", system.description(), "objects", objects.toString().strip(), "clauses",
				clauseText.toString().strip()));
		try {
			StructuredResult<PlainOutput> result = this.llmClient.call(new StructuredRequest("gap_plain", PLAIN_PROMPT,
					prompt, PLAIN_SCHEMA, this.judgmentProperties.temperature(), this.judgmentProperties.timeout()),
					PlainOutput.class);
			Set<Gap> gaps = new LinkedHashSet<>();
			result.value().gaps().forEach(g -> gaps.add(new Gap(g.object(), g.clauseRef(), g.judgment())));
			Object cost = this.llmCallMapper.summarizeIds(List.of(result.llmCallId())).get("costCny");
			return new Run(system.id(), "plain_prompt", "ok", gaps, millisSince(start), nz(result.inputTokens()),
					nz(result.outputTokens()), 1, (java.math.BigDecimal) cost);
		}
		catch (AppException ex) {
			return new Run(system.id(), "plain_prompt", ex.type().code(), Set.of(), millisSince(start), 0, 0, 1, null);
		}
	}

	private Run workflow(SimulatedSystem system, EvalContext context) {
		long start = System.nanoTime();
		long before = this.llmCallMapper.maxId();
		AssessProject project = this.assessmentService.create(new AssessmentRequests.CreateAssessment(
				"eval-gap " + BEIJING.format(context.startedAt()), system.name() + "（" + system.id() + "）", system.level()));
		Map<Long, String> keyById = new LinkedHashMap<>();
		for (SimulatedSystem.SimObject o : system.objects()) {
			JsonNode measures = EvalFiles.JSON.valueToTree(o.measures());
			var view = this.assessmentService.addObject(project.id(),
					new AssessmentRequests.AddObject(o.layer(), o.name(), o.description(), measures));
			keyById.put(view.id(), o.key());
		}
		AssessmentStatus status = this.analysisRunner.analyzeNow(project.id());
		Set<Gap> gaps = new LinkedHashSet<>();
		for (AssessFinding f : this.findingMapper.findByProject(project.id())) {
			if ("不符合".equals(f.judgment()) || "部分符合".equals(f.judgment())) {
				gaps.add(new Gap(keyById.get(f.objectId()), f.clauseRef(), f.judgment()));
			}
		}
		Map<String, Object> usage = this.llmCallMapper.summarizeSince(before, null);
		return new Run(system.id(), "workflow", status == AssessmentStatus.REVIEW ? "ok" : "failed", gaps,
				millisSince(start), number(usage.get("inputTokens")), number(usage.get("outputTokens")),
				(int) number(usage.get("calls")), (java.math.BigDecimal) usage.get("costCny"));
	}

	private static CryptoMeasures measures(SimulatedSystem.SimObject object) {
		return CryptoMeasures.parse(EvalFiles.JSON.writeValueAsString(object.measures()));
	}

	private static Set<Gap> expectedSet(SimulatedSystem system) {
		Set<Gap> set = new LinkedHashSet<>();
		system.expected().forEach(e -> set.add(new Gap(e.object(), e.clauseRef(), e.judgment())));
		return set;
	}

	private static List<Gap> matched(SimulatedSystem system, Run run) {
		return expectedSet(system).stream().filter(run.actual()::contains).toList();
	}

	private static List<Gap> missed(SimulatedSystem system, Run run) {
		return expectedSet(system).stream().filter(g -> !run.actual().contains(g)).toList();
	}

	private static List<Gap> falsePositives(SimulatedSystem system, Run run) {
		Set<Gap> expected = new HashSet<>(expectedSet(system));
		return run.actual().stream().filter(g -> !expected.contains(g)).toList();
	}

	private Map<String, Object> aggregate(List<SimulatedSystem> systems, List<Run> runs) {
		long expected = 0;
		long matched = 0;
		long actual = 0;
		long fp = 0;
		for (Run run : runs) {
			SimulatedSystem system = systems.stream().filter(s -> s.id().equals(run.system())).findFirst().orElseThrow();
			expected += system.expected().size();
			matched += matched(system, run).size();
			actual += run.actual().size();
			fp += falsePositives(system, run).size();
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("systems", runs.size());
		m.put("failedRuns", runs.stream().filter(r -> !r.status().equals("ok")).count());
		m.put("expected", expected);
		m.put("matched", matched);
		m.put("reported", actual);
		m.put("falsePositives", fp);
		m.put("recall", (expected == 0) ? null : (double) matched / expected);
		m.put("falsePositiveRate", (actual == 0) ? 0.0 : (double) fp / actual);
		m.put("avgLatencyMs", runs.stream().mapToLong(Run::latencyMs).average().orElse(0));
		m.put("inputTokens", runs.stream().mapToLong(Run::inputTokens).sum());
		m.put("outputTokens", runs.stream().mapToLong(Run::outputTokens).sum());
		m.put("calls", runs.stream().mapToInt(Run::calls).sum());
		m.put("costCny", runs.stream().map(Run::costCny).allMatch(java.util.Objects::isNull) ? null
				: runs.stream().map(Run::costCny).filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO,
						java.math.BigDecimal::add));
		return m;
	}

	@SuppressWarnings("unchecked")
	private String report(Map<String, Object> config, Map<String, Object> metrics, List<SimulatedSystem> systems,
			List<Run> runs) {
		StringBuilder md = new StringBuilder("# 差距识别评测报告\n\n## 运行信息\n\n");
		MarkdownTable info = new MarkdownTable("项", "值");
		config.forEach(info::row);
		md.append(info.render()).append('\n');
		md.append("## 两种方案对比\n\n");
		MarkdownTable table = new MarkdownTable("方案", "召回率", "误报率", "预期", "命中", "报出", "误报", "失败运行", "平均耗时 ms",
				"输入 token", "输出 token", "模型调用", "费用（元）");
		for (String approach : APPROACHES) {
			Map<String, Object> m = (Map<String, Object>) metrics.get(approach);
			table.row(approach.equals("workflow") ? "工作流 + 确定性规则" : "纯提示词", m.get("recall"),
					m.get("falsePositiveRate"), m.get("expected"), m.get("matched"), m.get("reported"),
					m.get("falsePositives"), m.get("failedRuns"), m.get("avgLatencyMs"), m.get("inputTokens"),
					m.get("outputTokens"), m.get("calls"), (m.get("costCny") == null) ? "未配置单价" : m.get("costCny"));
		}
		md.append(table.render()).append('\n');
		md.append("匹配规则：（测评对象, clause_ref, 判定）三者都一致才算命中。误报率 = 报出但不在预期中的差距项 / 报出总数，")
			.append("误报待复核：其中可能有预期遗漏的真实差距，最终数字以仓库所有者复核后为准。\n\n");
		md.append("## 每个系统\n\n");
		MarkdownTable per = new MarkdownTable("系统", "场景", "方案", "状态", "预期", "命中", "漏报", "误报", "耗时 ms", "token");
		for (Run run : runs) {
			SimulatedSystem s = systems.stream().filter(x -> x.id().equals(run.system())).findFirst().orElseThrow();
			per.row(s.id(), s.scenario(), run.approach(), run.status(), s.expected().size(), matched(s, run).size(),
					missed(s, run).size(), falsePositives(s, run).size(), run.latencyMs(),
					run.inputTokens() + run.outputTokens());
		}
		md.append(per.render()).append('\n');
		md.append("## 典型错误（漏报与误报，误报待复核）\n\n");
		MarkdownTable errors = new MarkdownTable("系统", "方案", "类型", "差距项", "复核结论（仓库所有者填写）");
		for (Run run : runs) {
			SimulatedSystem s = systems.stream().filter(x -> x.id().equals(run.system())).findFirst().orElseThrow();
			missed(s, run).forEach(g -> errors.row(s.id(), run.approach(), "漏报", label(g), ""));
			falsePositives(s, run).forEach(g -> errors.row(s.id(), run.approach(), "误报", label(g), ""));
		}
		md.append(errors.render());
		return md.toString();
	}

	private static String label(Gap gap) {
		return gap.object() + " / " + gap.clauseRef() + " / " + gap.judgment();
	}

	private static long number(Object value) {
		return (value == null) ? 0 : ((Number) value).longValue();
	}

	private static long nz(Integer value) {
		return (value == null) ? 0 : value;
	}

	private static long millisSince(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

}
