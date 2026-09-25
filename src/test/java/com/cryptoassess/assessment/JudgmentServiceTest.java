package com.cryptoassess.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.common.llm.LlmCallRecorder;
import com.cryptoassess.common.llm.LlmRequestOptions;
import com.cryptoassess.common.llm.StructuredLlmClient;
import com.cryptoassess.rules.AlgorithmRuleTable;
import com.cryptoassess.rules.OfficialScoringRules;
import com.cryptoassess.support.FakeChatModel;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class JudgmentServiceTest {

	private static final AlgorithmRuleTable RULES = AlgorithmRuleTable
		.load(new ClassPathResource("rules/algorithms.test.yaml"));

	private static final OfficialScoringRules SCORING = OfficialScoringRules
		.load(new ClassPathResource("rules/scoring.v2.test.yaml"));

	private static final JudgeInput.Clause STORAGE = new JudgeInput.Clause("FIX/T 0001-2026#5.4.3", "重要数据存储机密性",
			"数据库中保存的个人敏感信息和业务关键数据应加密存储。");

	private final FakeChatModel chatModel = new FakeChatModel();

	private JudgmentService service;

	@BeforeEach
	void setUp() {
		LlmCallRecorder recorder = mock(LlmCallRecorder.class);
		given(recorder.record(any())).willReturn(7L);
		service = new JudgmentService(
				new StructuredLlmClient(chatModel, recorder, "m", new LlmRequestOptions(Duration.ofSeconds(180))), RULES,
				SCORING, new JudgmentProperties(Duration.ofSeconds(5), 0.0), ObservationRegistry.NOOP);
	}

	private static JudgeInput input(String algorithm) {
		CryptoMeasures measures = new CryptoMeasures(null,
				List.of(new Measure(algorithm, null, "数据库加密组件", "字段级加密")), null, null, null);
		return new JudgeInput(3, "核心数据库", "应用和数据", "保存客户信息", measures, List.of(STORAGE));
	}

	/** 按 judge-output.v2 的格式构造模型输出。 */
	private static String output(String judgment, Boolean d, Boolean a, Boolean k, Integer moduleLevel,
			Boolean otherMet, String refs, String algorithms, String missing) {
		return """
				{"applicability":"%s","judgment":"%s","d":%s,"a":%s,"k":%s,"k_module_level":%s,"k_other_requirements_met":%s,
				 "evidence":"措施描述为字段级加密","missing_info":[%s],"rationale":"理由","remediation":"建议",
				 "clause_refs":[%s],"relevant_algorithms":[%s]}"""
			.formatted("不适用".equals(judgment) ? "不适用" : "适用", judgment, d, a, k, moduleLevel, otherMet, missing,
					refs, algorithms);
	}

	private static final String REF = "\"FIX/T 0001-2026#5.4.3\"";

	@Test
	void allDimensionsSatisfiedIsCompliant() {
		chatModel.enqueue(FakeChatModel.reply(output("符合", true, true, true, null, null, REF, "\"SM4-CBC\"", "")));

		Judgment j = service.judge(input("SM4-CBC"));

		assertThat(j.judgment()).isEqualTo("符合");
		assertThat(j.d()).isTrue();
		assertThat(j.a()).isTrue();
		assertThat(j.k()).isTrue();
		assertThat(j.ra()).isNull();
		assertThat(j.ruleOverride()).isFalse();
		assertThat(j.ruleChecks()).extracting(c -> c.name()).containsExactly("SM4");
		String prompt = chatModel.prompts().getFirst().getContents();
		assertThat(prompt).contains("[FIX/T 0001-2026#5.4.3]").contains("SM4-CBC").contains("APPROVED").contains("第3级")
			.contains("密码使用有效性");
	}

	@Test
	void ruleViolationForcesANotSatisfiedAndTheResultIsPartialPerTableOne() {
		// 模型认为 A 满足并给出“符合”，但相关算法 AES 被规则判为 NOT_APPROVED：A 改为不满足，按表 1 为“部分符合”
		chatModel.enqueue(FakeChatModel.reply(output("符合", true, true, true, null, null, REF, "\"AES-256-GCM\"", "")));

		Judgment j = service.judge(input("AES-256-GCM"));

		assertThat(j.a()).isFalse();
		assertThat(j.judgment()).isEqualTo("部分符合");
		assertThat(j.ruleOverride()).isTrue();
		assertThat(j.rationale()).contains("规则检查").contains("AES-256-GCM");
		// AES-256 安全强度 256 比特 → Ra = 1
		assertThat(j.ra()).isEqualByComparingTo("1");
	}

	@Test
	void raFollowsTheWeakestViolatingAlgorithm() {
		CryptoMeasures measures = new CryptoMeasures(null, List.of(new Measure("AES-128", null, null, "字段加密")),
				List.of(new Measure("MD5", null, null, "口令散列")), null, null);
		chatModel.enqueue(FakeChatModel
			.reply(output("部分符合", true, false, true, null, null, REF, "\"AES-128\",\"MD5\"", "")));

		Judgment j = service.judge(new JudgeInput(3, "核心数据库", "应用和数据", "", measures, List.of(STORAGE)));

		// MD5 安全强度 0 < 80 → Ra = 0.2
		assertThat(j.ra()).isEqualByComparingTo("0.2");
		assertThat(j.judgment()).isEqualTo("部分符合");
	}

	@Test
	void modelJudgedANotSatisfiedWithoutKnownStrengthLeavesRaForTheReviewer() {
		chatModel.enqueue(FakeChatModel.reply(output("部分符合", true, false, true, null, null, REF, "", "")));

		Judgment j = service.judge(input("SM4"));

		assertThat(j.a()).isFalse();
		assertThat(j.ra()).isNull();
		assertThat(j.pendingParameters()).containsExactly("Ra");
	}

	@Test
	void rkComesFromLevelAndModuleFacts() {
		chatModel.enqueue(FakeChatModel.reply(output("部分符合", true, true, false, 1, true, REF, "\"SM4\"", "")));

		Judgment j = service.judge(input("SM4"));

		// 第三级、一级密码模块、满足其他密钥管理要求 → Rk = 1.2
		assertThat(j.k()).isFalse();
		assertThat(j.rk()).isEqualByComparingTo("1.2");
		assertThat(j.judgment()).isEqualTo("部分符合");
	}

	@Test
	void dNotSatisfiedIsNonCompliantRegardlessOfOtherDimensions() {
		chatModel.enqueue(FakeChatModel.reply(output("部分符合", false, true, true, null, null, REF, "", "")));

		Judgment j = service.judge(input("SM4"));

		assertThat(j.judgment()).isEqualTo("不符合");
		assertThat(j.a()).isNull();
		assertThat(j.k()).isNull();
	}

	@Test
	void notApplicable() {
		chatModel.enqueue(FakeChatModel.reply(output("不适用", null, null, null, null, null, REF, "", "")));

		Judgment j = service.judge(input("SM4"));

		assertThat(j.judgment()).isEqualTo("不适用");
		assertThat(j.d()).isNull();
	}

	@Test
	void managementLayerUsesTheJudgmentDirectly() {
		JudgeInput mgmt = new JudgeInput(3, "密码管理制度", "管理制度", "制度文件", CryptoMeasures.EMPTY,
				List.of(new JudgeInput.Clause("FIX/T 0001-2026#6.1.1", "密码安全管理制度", "应制定管理制度。")));
		chatModel.enqueue(FakeChatModel
			.reply(output("部分符合", null, null, null, null, null, "\"FIX/T 0001-2026#6.1.1\"", "", "\"修订记录\"")));

		Judgment j = service.judge(mgmt);

		assertThat(j.judgment()).isEqualTo("部分符合");
		assertThat(j.d()).isNull();
		assertThat(j.missingInfo()).containsExactly("修订记录");
	}

	@Test
	void technicalOutputWithoutDIsInvalid() {
		chatModel.enqueue(FakeChatModel.reply(output("符合", null, null, null, null, null, REF, "", "")));

		assertThatThrownBy(() -> service.judge(input("SM4"))).isInstanceOf(AppException.class)
			.satisfies(ex -> assertThat(((AppException) ex).type()).isEqualTo(ErrorType.LLM_INVALID_OUTPUT))
			.hasMessageContaining("D");
	}

	@Test
	void citationOutsideInputClausesIsAnError() {
		chatModel.enqueue(FakeChatModel
			.reply(output("符合", true, true, true, null, null, "\"FIX/T 0001-2026#9.9\"", "", "")));

		assertThatThrownBy(() -> service.judge(input("SM4"))).isInstanceOf(AppException.class)
			.satisfies(ex -> assertThat(((AppException) ex).type()).isEqualTo(ErrorType.LLM_INVALID_OUTPUT))
			.hasMessageContaining("FIX/T 0001-2026#9.9");
	}

	@Test
	void invalidJsonAndSchemaViolationsAreErrors() {
		chatModel.enqueue(FakeChatModel.reply("判定：符合"));
		assertThatThrownBy(() -> service.judge(input("SM4"))).isInstanceOf(AppException.class);

		chatModel.enqueue(FakeChatModel.reply(output("基本符合", true, true, true, null, null, REF, "", "")));
		assertThatThrownBy(() -> service.judge(input("SM4"))).isInstanceOf(AppException.class)
			.hasMessageContaining("schema");
	}

}
