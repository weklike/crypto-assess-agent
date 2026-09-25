package com.cryptoassess.assessment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.common.llm.PromptTemplates;
import com.cryptoassess.common.llm.StructuredLlmClient;
import com.cryptoassess.common.llm.StructuredRequest;
import com.cryptoassess.common.llm.StructuredResult;
import com.cryptoassess.rules.AlgorithmRuleTable;
import com.cryptoassess.rules.OfficialScoringRules;
import com.cryptoassess.rules.RuleCheck;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

/**
 * 结构化判定：规则检查 → 模型判定（JSON Schema 校验）→ 代码层约束。
 * 技术层面按《量化评估规则（2023 版）》表 1 的 D/A/K 三个维度独立判定，总体判定由维度推出；管理层面直接判定。
 * 代码层约束：clause_refs 必须出自输入条款（否则按输出错误处理）；模型认为相关的算法被规则判为不合规时，
 * A 改为不满足并明确标注（按表 1 结果至多为“部分符合”）；Ra 由不合规算法的安全强度查规则得到，Rk 由等级和密码模块情况得到。
 */
@Service
public class JudgmentService {

	public static final String PROMPT_VERSION = "judge.v2";

	static final String SCHEMA = "judge-output.v2";

	private final StructuredLlmClient llmClient;

	private final AlgorithmRuleTable ruleTable;

	private final OfficialScoringRules scoringRules;

	private final JudgmentProperties properties;

	private final ObservationRegistry observations;

	public JudgmentService(StructuredLlmClient llmClient, AlgorithmRuleTable ruleTable, OfficialScoringRules scoringRules,
			JudgmentProperties properties, ObservationRegistry observations) {
		this.llmClient = llmClient;
		this.ruleTable = ruleTable;
		this.scoringRules = scoringRules;
		this.properties = properties;
		this.observations = observations;
	}

	record ModelOutput(String applicability, String judgment, Boolean d, Boolean a, Boolean k,
			@JsonProperty("k_module_level") Integer kModuleLevel,
			@JsonProperty("k_other_requirements_met") Boolean kOtherRequirementsMet, String evidence,
			@JsonProperty("missing_info") List<String> missingInfo, String rationale, String remediation,
			@JsonProperty("clause_refs") List<String> clauseRefs,
			@JsonProperty("relevant_algorithms") List<String> relevantAlgorithms) {
	}

	/** 该安全层面是否按 D/A/K 计分（密码应用技术要求）。 */
	public boolean technical(String layer) {
		return this.scoringRules.groupOf(layer).map(OfficialScoringRules.Group::dak).orElse(false);
	}

	/** 从对象的全部密码措施中抽取算法并查规则表，按出现顺序去重。 */
	public List<RuleCheck> ruleChecks(CryptoMeasures measures) {
		Observation observation = Observation.createNotStarted("rules.algorithm-check", this.observations)
			.lowCardinalityKeyValue("rules.version", this.ruleTable.ruleVersion());
		return observation.observe(() -> {
			List<RuleCheck> checks = extractChecks(measures);
			observation.highCardinalityKeyValue("rules.algorithms", String.valueOf(checks.size()));
			observation.highCardinalityKeyValue("rules.violations",
					String.valueOf(checks.stream().filter(c -> c.status().violates()).count()));
			return checks;
		});
	}

	private List<RuleCheck> extractChecks(CryptoMeasures measures) {
		Map<String, RuleCheck> checks = new LinkedHashMap<>();
		for (Measure measure : measures.all()) {
			String text = String.join(" ", nonNull(measure.algorithm()), nonNull(measure.protocol()),
					nonNull(measure.product()), nonNull(measure.evidence()));
			this.ruleTable.extract(text).forEach(check -> checks.putIfAbsent(check.name(), check));
		}
		return List.copyOf(checks.values());
	}

	public Judgment judge(JudgeInput input) {
		boolean technical = technical(input.layer());
		List<RuleCheck> checks = ruleChecks(input.measures());
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("level", input.level());
		variables.put("objectName", input.objectName());
		variables.put("layer", input.layer());
		variables.put("requirementType", technical ? "密码应用技术要求" : "密码应用管理要求");
		variables.put("description", nonNull(input.description()).isBlank() ? "（未填写）" : input.description());
		variables.put("measures", formatMeasures(input.measures()));
		variables.put("ruleChecks", formatChecks(checks));
		variables.put("clauses", formatClauses(input.clauses()));
		variables.put("judgmentRule", technical
				? "judgment：按 d、a、k 给出你的总体看法即可，最终判定由系统按表 1 从 d、a、k 推出（d 不满足为不符合，三者都满足为符合，其余为部分符合）。"
				: "本对象属于管理要求：d、a、k、k_module_level、k_other_requirements_met 都填 null，直接给出 judgment（符合 / 部分符合 / 不符合）。");
		String prompt = PromptTemplates.render(PROMPT_VERSION, variables);
		StructuredResult<ModelOutput> result = this.llmClient.call(new StructuredRequest("judge", PROMPT_VERSION, prompt,
				SCHEMA, this.properties.temperature(), this.properties.timeout()), ModelOutput.class);
		ModelOutput out = result.value();

		Set<String> allowed = new HashSet<>(input.clauses().stream().map(JudgeInput.Clause::clauseRef).toList());
		List<String> outside = out.clauseRefs().stream().filter(ref -> !allowed.contains(ref)).toList();
		if (!outside.isEmpty()) {
			throw new AppException(ErrorType.LLM_INVALID_OUTPUT,
					"judgment cites clauses that were not provided: " + outside);
		}
		if ("不适用".equals(out.applicability()) || "不适用".equals(out.judgment())) {
			return new Judgment("不适用", null, null, null, null, null, null, out.evidence(), out.missingInfo(),
					out.rationale(), out.remediation(), out.clauseRefs(), out.relevantAlgorithms(), checks, false, List.of(),
					result.llmCallId());
		}
		if (!technical) {
			return new Judgment(out.judgment(), null, null, null, null, null, null, out.evidence(), out.missingInfo(),
					out.rationale(), out.remediation(), out.clauseRefs(), out.relevantAlgorithms(), checks, false, List.of(),
					result.llmCallId());
		}
		return technicalJudgment(input, out, checks, result.llmCallId());
	}

	private Judgment technicalJudgment(JudgeInput input, ModelOutput out, List<RuleCheck> checks, long llmCallId) {
		if (out.d() == null) {
			throw new AppException(ErrorType.LLM_INVALID_OUTPUT, "technical judgment must give D (密码使用有效性)");
		}
		if (!out.d()) {
			return new Judgment("不符合", false, null, null, null, null, out.kModuleLevel(), out.evidence(),
					out.missingInfo(), out.rationale(), out.remediation(), out.clauseRefs(), out.relevantAlgorithms(),
					checks, false, List.of(), llmCallId);
		}
		if (out.a() == null || out.k() == null) {
			throw new AppException(ErrorType.LLM_INVALID_OUTPUT, "technical judgment must give A and K when D is true");
		}
		List<RuleCheck> violated = new ArrayList<>();
		for (String algorithm : out.relevantAlgorithms()) {
			RuleCheck check = this.ruleTable.check(algorithm);
			if (check.status().violates()) {
				violated.add(check);
			}
		}
		boolean a = out.a() && violated.isEmpty();
		boolean override = out.a() && !violated.isEmpty();
		String rationale = out.rationale();
		if (override) {
			rationale = "【规则检查】相关算法 "
					+ String.join("、", violated.stream().map(c -> c.input() + "（" + c.status() + "）").toList())
					+ " 被规则表判为不合规，密码算法/技术合规性（A）改为不满足。模型原理由：" + rationale;
		}
		List<String> pending = new ArrayList<>();
		BigDecimal ra = null;
		if (!a) {
			ra = raFor(violated);
			if (ra == null) {
				pending.add("Ra");
			}
		}
		BigDecimal rk = out.k() ? null
				: this.scoringRules.rk(input.level(), out.kModuleLevel(), Boolean.TRUE.equals(out.kOtherRequirementsMet()));
		return new Judgment(Judgment.fromDimensions(true, a, out.k()), true, a, out.k(), ra, rk, out.kModuleLevel(),
				out.evidence(), out.missingInfo(), rationale, out.remediation(), out.clauseRefs(), out.relevantAlgorithms(),
				checks, override, List.copyOf(pending), llmCallId);
	}

	/** 取不合规算法中最弱的安全强度；有任一算法没有安全强度（或 A 由模型判定、没有对应算法）时交给复核。 */
	private BigDecimal raFor(List<RuleCheck> violated) {
		if (violated.isEmpty() || violated.stream().anyMatch(c -> c.securityBits() == null)) {
			return null;
		}
		int weakest = violated.stream().mapToInt(RuleCheck::securityBits).min().orElseThrow();
		return this.scoringRules.ra(weakest);
	}

	/** 供评测等其他包复用的措施格式化（与判定提示词中的写法一致）。 */
	public static String formatMeasuresPublic(CryptoMeasures measures) {
		return formatMeasures(measures);
	}

	static String formatMeasures(CryptoMeasures measures) {
		Map<String, String> names = Map.of("transport", "传输", "storage", "存储", "auth", "身份鉴别", "key_mgmt", "密钥管理",
				"other", "其他");
		StringBuilder out = new StringBuilder();
		measures.byCategory().forEach((category, list) -> {
			for (Measure m : list) {
				out.append("- [").append(names.get(category)).append("] ");
				List<String> parts = new ArrayList<>();
				if (m.algorithm() != null) {
					parts.add("算法 " + m.algorithm());
				}
				if (m.protocol() != null) {
					parts.add("协议 " + m.protocol());
				}
				if (m.product() != null) {
					parts.add("产品 " + m.product());
				}
				if (m.evidence() != null) {
					parts.add("证据 " + m.evidence());
				}
				out.append(String.join("；", parts)).append('\n');
			}
		});
		return out.isEmpty() ? "（未填写密码措施）" : out.toString().strip();
	}

	static String formatChecks(List<RuleCheck> checks) {
		if (checks.isEmpty()) {
			return "（措施中未识别出规则表里的算法）";
		}
		StringBuilder out = new StringBuilder();
		for (RuleCheck c : checks) {
			out.append("- ").append(c.input()).append(" → ").append(c.name()).append("：").append(c.status())
				.append("（").append(c.basis()).append("）\n");
		}
		return out.toString().strip();
	}

	static String formatClauses(List<JudgeInput.Clause> clauses) {
		StringBuilder out = new StringBuilder();
		for (JudgeInput.Clause c : clauses) {
			out.append('[').append(c.clauseRef()).append("] ").append(c.title()).append('\n').append(c.body()).append("\n\n");
		}
		return out.toString().strip();
	}

	private static String nonNull(String value) {
		return (value == null) ? "" : value;
	}

}
