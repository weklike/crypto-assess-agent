package com.cryptoassess.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从已确认的差距项计算得分（纯函数）：测评对象（D/A/K 或管理判定）→ 测评单元（对象平均）
 * → 安全层面（按等级的单元权重加权）→ 总分（技术 70 + 管理 30）。每一分都写进明细。
 */
public final class OfficialScoringCalculator {

	private OfficialScoringCalculator() {
	}

	/**
	 * 一条已确认的差距项。技术层面用 d/a/k/ra/rk，管理层面用 judgment。
	 *
	 * @param clauseTitle 条款标题，用于没有显式映射时按标题匹配测评单元
	 * @param applicable false 表示判定为“不适用”
	 */
	public record Item(String objectKey, String objectName, String layer, String clauseRef, String clauseTitle,
			boolean applicable, String judgment, Boolean d, Boolean a, Boolean k, BigDecimal ra, BigDecimal rk) {

		public static Item technical(String objectKey, String objectName, String layer, String clauseRef,
				String clauseTitle, Boolean d, Boolean a, Boolean k, BigDecimal ra, BigDecimal rk) {
			return new Item(objectKey, objectName, layer, clauseRef, clauseTitle, true, null, d, a, k, ra, rk);
		}

		public static Item management(String objectKey, String objectName, String layer, String clauseRef,
				String clauseTitle, String judgment) {
			return new Item(objectKey, objectName, layer, clauseRef, clauseTitle, true, judgment, null, null, null, null,
					null);
		}

		public static Item notApplicable(String objectKey, String objectName, String layer, String clauseRef,
				String clauseTitle) {
			return new Item(objectKey, objectName, layer, clauseRef, clauseTitle, false, null, null, null, null, null,
					null);
		}

	}

	public static OfficialScoreResult calculate(OfficialScoringRules rules, int level, List<Item> items) {
		if (level < 1 || level > 4) {
			throw new IllegalArgumentException("level must be 1-4");
		}
		List<String> detail = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		detail.add("规则 " + rules.version() + "（《商用密码应用安全性评估量化评估规则（2023 版）》）；第" + level
				+ "级；对象、单元、层面保留 " + rules.intermediateScale() + " 位，总分保留 " + rules.totalScale() + " 位");
		Map<String, List<BigDecimal>> unitScores = new LinkedHashMap<>();
		Map<String, OfficialScoringRules.Unit> unitsByKey = new LinkedHashMap<>();
		Map<String, List<BigDecimal>> objectScores = new LinkedHashMap<>();

		for (Item item : items) {
			String label = item.objectName() + " / " + item.clauseRef();
			if (!item.applicable()) {
				detail.add(label + "：不适用，不参与计算");
				continue;
			}
			if (rules.isUnscored(item.clauseRef(), item.clauseTitle())) {
				detail.add(label + "：通用要求或密码产品/服务指标，不单独评价");
				continue;
			}
			Optional<OfficialScoringRules.Group> group = rules.groupOf(item.layer());
			if (group.isEmpty()) {
				errors.add(label + "：安全层面 " + item.layer() + " 不在评分规则中");
				continue;
			}
			Optional<OfficialScoringRules.Unit> unit = rules.findUnit(item.layer(), item.clauseRef(), item.clauseTitle());
			if (unit.isEmpty()) {
				errors.add(label + "：未映射到任何测评单元（在 units[].clause_refs 中补映射）");
				continue;
			}
			BigDecimal weight = unit.get().weightFor(level);
			if (weight == null) {
				detail.add(label + "：测评单元“" + unit.get().name() + "”在第" + level + "级不评价");
				continue;
			}
			BigDecimal score;
			try {
				score = group.get().dak() ? OfficialScoring.objectScore(item.d(), item.a(), item.k(), item.ra(), item.rk())
						: rules.managementScore(item.judgment());
			}
			catch (IllegalArgumentException ex) {
				errors.add(label + "：" + ex.getMessage());
				continue;
			}
			unitScores.computeIfAbsent(unit.get().key(), key -> new ArrayList<>()).add(score);
			unitsByKey.put(unit.get().key(), unit.get());
			objectScores.computeIfAbsent(item.objectKey(), key -> new ArrayList<>()).add(score);
			detail.add(label + " → " + unit.get().key() + "：" + describe(group.get(), item) + " = " + score.toPlainString());
		}
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("cannot score " + errors.size() + " finding(s): " + String.join("; ", errors));
		}

		Map<String, BigDecimal> units = new LinkedHashMap<>();
		unitScores.forEach((key, scores) -> {
			BigDecimal mean = OfficialScoring.unitMean(scores);
			units.put(key, mean);
			detail.add("测评单元 " + key + "（权重 " + unitsByKey.get(key).weightFor(level).toPlainString() + "）："
					+ scores.size() + " 个对象平均 = " + mean.toPlainString());
		});

		Map<String, BigDecimal> layers = new LinkedHashMap<>();
		for (String layer : rules.layers().keySet()) {
			List<BigDecimal> scores = new ArrayList<>();
			List<BigDecimal> weights = new ArrayList<>();
			units.forEach((key, score) -> {
				OfficialScoringRules.Unit u = unitsByKey.get(key);
				if (u.layer().equals(layer)) {
					scores.add(score);
					weights.add(u.weightFor(level));
				}
			});
			if (scores.isEmpty()) {
				layers.put(layer, null);
				detail.add("安全层面 " + layer + "：没有适用的测评单元，不参与计算");
			}
			else {
				BigDecimal score = OfficialScoring.weightedMean(scores, weights);
				layers.put(layer, score);
				detail.add("安全层面 " + layer + "（权重 " + rules.layerWeight(layer).toPlainString() + "）：单元加权平均 = "
						+ score.toPlainString());
			}
		}

		OfficialScoring.TotalResult total = OfficialScoring.total(rules, layers);
		Map<String, BigDecimal> groups = new LinkedHashMap<>();
		total.groupScores().forEach((name, avg) -> groups.put(name, OfficialScoring.round(avg)));
		detail.add((total.total() == null) ? "总分：" + total.note()
				: "总分 = 70 × 技术组加权平均 + 30 × 管理组加权平均 = " + total.total().toPlainString());

		Map<String, BigDecimal> objects = new LinkedHashMap<>();
		objectScores.forEach((key, scores) -> objects.put(key, OfficialScoring.unitMean(scores)));
		return new OfficialScoreResult(total.total(), total.note(), groups, java.util.Collections.unmodifiableMap(layers),
				units, objects, List.copyOf(detail), rules.version(), level);
	}

	private static String describe(OfficialScoringRules.Group group, Item item) {
		if (!group.dak()) {
			return "管理判定“" + item.judgment() + "”";
		}
		return OfficialScoring.describe(item.d(), item.a(), item.k(), item.ra(), item.rk());
	}

}
