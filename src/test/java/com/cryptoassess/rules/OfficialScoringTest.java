package com.cryptoassess.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 按《量化评估规则（2023 版）》计算。分阶段用例来自调研交付包中独立计算的 35 个算术候选（AR01–AR35）
 * 和 1 个零分母边界（AR36），与本实现互为交叉核对。
 */
class OfficialScoringTest {

	private static final OfficialScoringRules PROD = OfficialScoringRules
		.load(new ClassPathResource("rules/scoring.v2.yaml"));

	private static final OfficialScoringRules TEST = OfficialScoringRules
		.load(new ClassPathResource("rules/scoring.v2.test.yaml"));

	@SuppressWarnings("unchecked")
	static Stream<Arguments> researchCandidates() throws IOException {
		try (InputStream in = new ClassPathResource("scoring/research_arithmetic_candidates.yaml").getInputStream()) {
			Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
			return ((List<Map<String, Object>>) root.get("cases")).stream().map(c -> Arguments.of(c.get("id"), c));
		}
	}

	private static BigDecimal dec(Object value) {
		return (value == null) ? null : new BigDecimal(String.valueOf(value));
	}

	@SuppressWarnings("unchecked")
	@ParameterizedTest(name = "{0}")
	@MethodSource("researchCandidates")
	void researchArithmeticCandidates(String id, Map<String, Object> c) {
		Map<String, Object> in = (Map<String, Object>) c.get("inputs");
		Object expected = c.get("candidate_expected");
		BigDecimal actual = switch (String.valueOf(c.get("kind"))) {
			case "object_dak" -> OfficialScoring.objectScore((Boolean) in.get("D"), (Boolean) in.get("A"),
					(Boolean) in.get("K"), dec(in.get("Ra")), dec(in.get("Rk")));
			case "compensation" -> OfficialScoring.compensate(dec(in.get("PA")), dec(in.get("PB")));
			case "unit_mean" -> OfficialScoring
				.unitMean(((List<Object>) in.get("object_scores")).stream().map(OfficialScoringTest::dec).toList());
			case "layer_weighted" -> {
				List<BigDecimal> scores = ((List<Object>) in.get("unit_scores")).stream().map(OfficialScoringTest::dec).toList();
				List<BigDecimal> weights = ((List<Object>) in.get("unit_weights")).stream().map(OfficialScoringTest::dec).toList();
				yield OfficialScoring.weightedMean(scores, weights);
			}
			case "management_unit" -> PROD.managementScore(String.valueOf(in.get("judgment")));
			case "total", "undefined_boundary" -> {
				Map<String, BigDecimal> layers = new LinkedHashMap<>();
				((Map<String, Object>) in.get("layer_scores")).forEach((k, v) -> layers.put(k, dec(v)));
				yield OfficialScoring.total(PROD, layers).total();
			}
			default -> throw new IllegalArgumentException(String.valueOf(c.get("kind")));
		};
		assertThat((actual == null) ? null : actual.toPlainString()).as(id).isEqualTo(expected);
	}

	@Test
	void productionRulesMatchOfficialLayerAndGroupWeights() {
		assertThat(PROD.version()).isEqualTo("scoring.v2");
		assertThat(PROD.layerWeight("管理制度")).isEqualByComparingTo("8");
		assertThat(PROD.layerWeight("应急处置")).isEqualByComparingTo("6");
		assertThat(PROD.units()).hasSize(41);
		assertThat(PROD.unit("网络和通信", "通信数据完整性").weightFor(4)).isEqualByComparingTo("1");
		assertThat(PROD.unit("网络和通信", "通信数据完整性").weightFor(3)).isEqualByComparingTo("0.7");
		assertThat(PROD.unit("设备和计算", "远程管理通道安全").weightFor(2)).isNull();
		assertThat(PROD.unit("人员管理", "建立密码应用岗位责任制度").weightFor(1)).isNull();
		assertThat(PROD.ra(128)).isEqualByComparingTo("1");
		assertThat(PROD.ra(112)).isEqualByComparingTo("1");
		assertThat(PROD.ra(80)).isEqualByComparingTo("0.5");
		assertThat(PROD.ra(56)).isEqualByComparingTo("0.2");
		assertThat(PROD.rk(3, 1, true)).isEqualByComparingTo("1.2");
		assertThat(PROD.rk(3, 2, true)).isEqualByComparingTo("1");
		assertThat(PROD.rk(3, 1, false)).isEqualByComparingTo("1");
		assertThat(PROD.rk(4, 2, true)).isEqualByComparingTo("1.5");
		assertThat(PROD.rk(2, 1, true)).isEqualByComparingTo("1");
	}

	private static OfficialScoringCalculator.Item tech(String object, String clause, boolean d, Boolean a, Boolean k,
			String ra, String rk) {
		return OfficialScoringCalculator.Item.technical(object, object, layerOf(clause), "FIX/T 0001-2026#" + clause,
				null, d, a, k, dec(ra), dec(rk));
	}

	private static OfficialScoringCalculator.Item mgmt(String clause, String judgment) {
		return OfficialScoringCalculator.Item.management("m", "管理", layerOf(clause), "FIX/T 0001-2026#" + clause, null,
				judgment);
	}

	private static String layerOf(String clause) {
		return switch (clause.substring(0, 3)) {
			case "5.1" -> "物理和环境";
			case "5.2" -> "网络和通信";
			case "5.3" -> "设备和计算";
			case "5.4", "4.1", "4.2" -> "应用和数据";
			case "6.1" -> "管理制度";
			case "6.2" -> "人员管理";
			case "6.3" -> "建设运行";
			default -> "应急处置";
		};
	}

	@Test
	void fullPipelineUsesLevelWeightsObjectMeanAndGroups() {
		List<OfficialScoringCalculator.Item> items = new ArrayList<>();
		// 应用和数据（第三级）：5.4.3 存储机密性 权重 1；两个对象 1 与 0.5（A 不满足、Ra=1）→ 单元 0.75
		items.add(tech("db", "5.4.3", true, true, true, null, null));
		items.add(tech("cache", "5.4.3", true, false, true, "1", null));
		// 5.4.4 存储完整性 权重 0.7：D 不满足 → 0
		items.add(tech("db", "5.4.4", false, null, null, null, null));
		// 通用要求 4.1 不单独评价
		items.add(tech("db", "4.1", true, true, true, null, null));
		// 管理制度 6.1.1 权重 1：部分符合 0.5；应急 6.4.1 权重 1：符合 1
		items.add(mgmt("6.1.1", "部分符合"));
		items.add(mgmt("6.4.1", "符合"));

		OfficialScoreResult r = OfficialScoringCalculator.calculate(TEST, 3, items);

		// 应用和数据 = (1×0.75 + 0.7×0) / 1.7 = 0.4412；技术组只有这一层 → 0.4412×70 = 30.884
		assertThat(r.layers().get("应用和数据")).isEqualByComparingTo("0.4412");
		assertThat(r.units().get("应用和数据|重要数据存储机密性")).isEqualByComparingTo("0.7500");
		// 管理组 = (8×0.5 + 6×1) / 14 = 0.714285… ×30 = 21.4286；总分 30.884 + 21.4286 = 52.31
		assertThat(r.layers().get("管理制度")).isEqualByComparingTo("0.5000");
		assertThat(r.total()).isEqualByComparingTo("52.31");
		assertThat(r.totalNote()).isNull();
		assertThat(r.detail()).anyMatch(l -> l.contains("4.1") && l.contains("不单独评价"));
	}

	@Test
	void unitNotEvaluatedAtThisLevelIsSkipped() {
		// 5.4.5 不可否认性在第一、二级为“/”
		OfficialScoreResult r = OfficialScoringCalculator.calculate(TEST, 2,
				List.of(tech("db", "5.4.5", false, null, null, null, null), tech("db", "5.4.3", true, true, true, null, null),
						mgmt("6.1.1", "符合")));

		assertThat(r.units()).doesNotContainKey("应用和数据|不可否认性");
		assertThat(r.total()).isEqualByComparingTo("100.00");
	}

	@Test
	void notApplicableFindingsDoNotCount() {
		OfficialScoreResult r = OfficialScoringCalculator.calculate(TEST, 3,
				List.of(tech("db", "5.4.3", true, true, true, null, null),
						OfficialScoringCalculator.Item.notApplicable("db", "db", "应用和数据", "FIX/T 0001-2026#5.4.4", null),
						mgmt("6.1.1", "符合")));

		assertThat(r.layers().get("应用和数据")).isEqualByComparingTo("1.0000");
		assertThat(r.total()).isEqualByComparingTo("100.00");
	}

	@Test
	void wholeGroupNotApplicableGivesNoTotalAndAReason() {
		OfficialScoreResult r = OfficialScoringCalculator.calculate(TEST, 3,
				List.of(tech("db", "5.4.3", true, true, true, null, null)));

		assertThat(r.total()).isNull();
		assertThat(r.totalNote()).contains("密码应用管理要求").contains("人工");
		assertThat(r.layers().get("应用和数据")).isEqualByComparingTo("1.0000");
	}

	@Test
	void missingDimensionsOrParametersAreErrorsNotGuesses() {
		assertThatThrownBy(() -> OfficialScoringCalculator.calculate(TEST, 3,
				List.of(tech("db", "5.4.3", true, false, true, null, null))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Ra");
		assertThatThrownBy(() -> OfficialScoringCalculator.calculate(TEST, 3,
				List.of(tech("db", "5.4.3", true, true, false, null, null))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Rk");
		assertThatThrownBy(() -> OfficialScoringCalculator.calculate(TEST, 3,
				List.of(OfficialScoringCalculator.Item.technical("db", "db", "应用和数据", "FIX/T 0001-2026#5.4.3", null, null,
						null, null, null, null))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("D/A/K");
	}

	@Test
	void unmappedClauseIsReportedAndTitleMatchIsTheFallback() {
		OfficialScoringCalculator.Item byTitle = OfficialScoringCalculator.Item.technical("db", "db", "应用和数据",
				"GB/T 39786-2021#9.9", "重要数据存储机密性", true, true, true, null, null);
		OfficialScoreResult r = OfficialScoringCalculator.calculate(PROD, 3, List.of(byTitle, mgmtProd("符合")));
		assertThat(r.units()).containsKey("应用和数据|重要数据存储机密性");

		OfficialScoringCalculator.Item unknown = OfficialScoringCalculator.Item.technical("db", "db", "应用和数据",
				"GB/T 39786-2021#9.8", "某个不存在的指标", true, true, true, null, null);
		assertThatThrownBy(() -> OfficialScoringCalculator.calculate(PROD, 3, List.of(unknown)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("GB/T 39786-2021#9.8")
			.hasMessageContaining("测评单元");
	}

	private static OfficialScoringCalculator.Item mgmtProd(String judgment) {
		return OfficialScoringCalculator.Item.management("m", "管理", "管理制度", "GB/T 39786-2021#8.1", "具备密码应用安全管理制度",
				judgment);
	}

	@Test
	void invalidRulesFailWithReason() {
		assertThatThrownBy(() -> OfficialScoringRules.load(new ByteArrayResource("""
				format: official-2023
				version: x
				rounding: {intermediate_scale: 4, total_scale: 2, mode: HALF_UP}
				management_scores: {符合: 1, 部分符合: 0.5, 不符合: 0}
				ra: [{min_bits: 0, value: 0.2}]
				rk: {"3": {default: 1}}
				groups: [{name: g, weight: 70, scoring: dak, layers: [物理和环境]}]
				layers: {物理和环境: 10}
				units: [{layer: 网络和通信, name: x, weights: {3: 1}}]
				""".getBytes(), "bad.yaml"))).isInstanceOf(RuleTableException.class).hasMessageContaining("网络和通信");
	}

	@Test
	void subClausesBelongToTheMappedUnitOrUnscoredChapter() {
		// 测评单元映射到单元条款号，其下的测评指标、测评对象、测评实施子条款都归入同一单元
		assertThat(TEST.findUnit("应用和数据", "FIX/T 0001-2026#5.4.3.2", null)).map(OfficialScoringRules.Unit::key)
			.contains("应用和数据|重要数据存储机密性");
		assertThat(TEST.findUnit("应用和数据", "FIX/T 0001-2026#5.4.30", null)).isEmpty();
		assertThat(TEST.isUnscored("FIX/T 0001-2026#4.1.2", null)).isTrue();
		assertThat(TEST.isUnscored("FIX/T 0001-2026#4.10", null)).isFalse();
	}

	@Test
	void productionUnitsMapToGbt43206MeasurementUnits() {
		// 映射来自调研的测评单元目录（docs/crypto_research/测评单元_标准编号对照_v2.csv），41 个单元一一对应
		assertThat(PROD.units()).allSatisfy(u -> assertThat(u.clauseRefs()).hasSize(1)
			.allMatch(ref -> ref.matches("GB/T 43206-2023#[67]\\.[1-4]\\.[1-8]")));
		assertThat(PROD.units().stream().flatMap(u -> u.clauseRefs().stream()).distinct()).hasSize(41);
		assertThat(PROD.findUnit("物理和环境", "GB/T 43206-2023#6.1.2.1", null)).map(OfficialScoringRules.Unit::key)
			.contains("物理和环境|电子门禁记录数据存储完整性");
		assertThat(PROD.findUnit("应用和数据", "GB/T 43206-2023#6.4.5.3", null)).map(OfficialScoringRules.Unit::key)
			.contains("应用和数据|重要数据存储机密性");
		assertThat(PROD.findUnit("管理制度", "GB/T 43206-2023#7.1.4.1", null)).map(OfficialScoringRules.Unit::key)
			.contains("管理制度|定期修订安全管理制度");
		assertThat(PROD.findUnit("建设运行", "GB/T 43206-2023#7.3.5.1", null)).map(OfficialScoringRules.Unit::key)
			.contains("建设运行|定期开展密码应用安全性评估及攻防对抗演习");
		// 管理单元用 GB/T 43206 的标题也能按标题匹配
		assertThat(PROD.findUnit("人员管理", "X#1", "安全岗位考核")).map(OfficialScoringRules.Unit::key)
			.contains("人员管理|定期进行安全岗位人员考核");
		// 通则、通用测评要求、资料性附录不单独评价
		assertThat(PROD.isUnscored("GB/T 43206-2023#5.3.1", null)).isTrue();
		assertThat(PROD.isUnscored("GB/T 43206-2023#4", null)).isTrue();
		assertThat(PROD.isUnscored("GB/T 43206-2023#A.1", null)).isTrue();
		assertThat(PROD.isUnscored("GB/T 39786-2021#5", null)).isTrue();
		assertThat(PROD.isUnscored("GB/T 43206-2023#6.1.1.1", null)).isFalse();
	}

}
