package com.cryptoassess.rules;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 《量化评估规则（2023 版）》第 5 章各阶段的计算，纯函数。对象、单元、层面四舍五入到 4 位，总分到 2 位。
 */
public final class OfficialScoring {

	static final int SCALE = 4;

	private static final MathContext MC = MathContext.DECIMAL128;

	private static final BigDecimal HALF = new BigDecimal("0.5");

	private static final BigDecimal QUARTER = new BigDecimal("0.25");

	private OfficialScoring() {
	}

	/**
	 * 表 1：D 不满足 = 0；D、A、K 都满足 = 1；A 不满足 = 0.5·Ra；K 不满足 = 0.5·Rk；A、K 都不满足 = 0.25·Ra·Rk。
	 * 三个维度独立判定；缺少必要的维度或参数时报错，不猜。
	 */
	public static BigDecimal objectScore(Boolean d, Boolean a, Boolean k, BigDecimal ra, BigDecimal rk) {
		if (d == null) {
			throw new IllegalArgumentException("D/A/K: D (密码使用有效性) is required");
		}
		if (!d) {
			return round(BigDecimal.ZERO);
		}
		if (a == null || k == null) {
			throw new IllegalArgumentException("D/A/K: A and K are required when D is satisfied");
		}
		if (!a && ra == null) {
			throw new IllegalArgumentException("Ra is required when A (密码算法/技术合规性) is not satisfied");
		}
		if (!k && rk == null) {
			throw new IllegalArgumentException("Rk is required when K (密钥管理安全) is not satisfied");
		}
		BigDecimal score;
		if (a && k) {
			score = BigDecimal.ONE;
		}
		else if (!a && k) {
			score = HALF.multiply(ra, MC);
		}
		else if (a) {
			score = HALF.multiply(rk, MC);
		}
		else {
			score = QUARTER.multiply(ra, MC).multiply(rk, MC);
		}
		return round(score);
	}

	/** 测评对象 A 弥补了 B 的不足时，B 的分值为 MAX(0.5×PA, PB)。 */
	public static BigDecimal compensate(BigDecimal pa, BigDecimal pb) {
		return round(HALF.multiply(pa, MC).max(pb));
	}

	public static BigDecimal unitMean(List<BigDecimal> scores) {
		BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return round(sum.divide(BigDecimal.valueOf(scores.size()), MC));
	}

	public static BigDecimal weightedMean(List<BigDecimal> scores, List<BigDecimal> weights) {
		BigDecimal numerator = BigDecimal.ZERO;
		BigDecimal denominator = BigDecimal.ZERO;
		for (int i = 0; i < scores.size(); i++) {
			numerator = numerator.add(scores.get(i).multiply(weights.get(i), MC), MC);
			denominator = denominator.add(weights.get(i));
		}
		return round(numerator.divide(denominator, MC));
	}

	/**
	 * @param groupScores 各组的层面加权平均（未舍入，展示用时再舍入）
	 * @param note 不出总分时的原因
	 */
	public record TotalResult(BigDecimal total, Map<String, BigDecimal> groupScores, String note) {
	}

	/**
	 * S = Σ 组权重 × 组内适用层面的加权平均；层面得分为 null 表示全部不适用、不参与。
	 * 某组全部不适用时公式分母为 0，按本项目约定不出总分、转人工。
	 */
	public static TotalResult total(OfficialScoringRules rules, Map<String, BigDecimal> layerScores) {
		Map<String, BigDecimal> groupScores = new LinkedHashMap<>();
		BigDecimal total = BigDecimal.ZERO;
		for (OfficialScoringRules.Group group : rules.groups()) {
			BigDecimal numerator = BigDecimal.ZERO;
			BigDecimal denominator = BigDecimal.ZERO;
			for (String layer : group.layers()) {
				BigDecimal score = layerScores.get(layer);
				if (score != null) {
					BigDecimal weight = rules.layerWeight(layer);
					numerator = numerator.add(weight.multiply(score, MC), MC);
					denominator = denominator.add(weight);
				}
			}
			if (denominator.signum() == 0) {
				return new TotalResult(null, groupScores,
						group.name() + "的所有安全层面都不适用，量化公式分母为 0，不出总分，转人工判断");
			}
			BigDecimal average = numerator.divide(denominator, MC);
			groupScores.put(group.name(), average);
			total = total.add(group.weight().multiply(average, MC), MC);
		}
		return new TotalResult(total.setScale(rules.totalScale(), rules.roundingMode()), groupScores, null);
	}

	/** 维度的简写，如“D√ A× K√ Ra=0.5”，用于计算明细和报告。 */
	public static String describe(Boolean d, Boolean a, Boolean k, BigDecimal ra, BigDecimal rk) {
		if (Boolean.FALSE.equals(d)) {
			return "D×";
		}
		StringBuilder s = new StringBuilder("D").append(flag(d)).append(" A").append(flag(a)).append(" K").append(flag(k));
		if (ra != null && Boolean.FALSE.equals(a)) {
			s.append(" Ra=").append(ra.stripTrailingZeros().toPlainString());
		}
		if (rk != null && Boolean.FALSE.equals(k)) {
			s.append(" Rk=").append(rk.stripTrailingZeros().toPlainString());
		}
		return s.toString();
	}

	private static String flag(Boolean value) {
		return (value == null) ? "/" : (value ? "√" : "×");
	}

	static BigDecimal round(BigDecimal value) {
		return value.setScale(SCALE, RoundingMode.HALF_UP);
	}

}
