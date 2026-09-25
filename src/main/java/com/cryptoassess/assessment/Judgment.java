package com.cryptoassess.assessment;

import java.math.BigDecimal;
import java.util.List;

import com.cryptoassess.rules.RuleCheck;

/**
 * 结构化判定结果。技术层面按 D/A/K 三个维度独立判定（《量化评估规则（2023 版）》表 1），总体判定由维度推出；
 * 管理层面直接给出判定。
 *
 * @param d 密码使用有效性；管理层面或不适用时为 null
 * @param a 密码算法/技术合规性；D 不满足时为 null
 * @param k 密钥管理安全；D 不满足时为 null
 * @param ra A 不满足时的修正参数（按算法安全强度查规则表），无法确定时为 null 并记入 pendingParameters
 * @param rk K 不满足时的修正参数（按等级和密码模块情况）
 * @param moduleLevel 模型从描述中抽取的密码模块安全等级
 * @param ruleChecks 从对象密码措施中抽取出的算法及其规则检查结果
 * @param ruleOverride 相关算法被规则表判为不合规，A 由代码改为不满足
 * @param pendingParameters 需要测评人员在复核时补充的参数（如 Ra）
 */
public record Judgment(String judgment, Boolean d, Boolean a, Boolean k, BigDecimal ra, BigDecimal rk,
		Integer moduleLevel, String evidence, List<String> missingInfo, String rationale, String remediation,
		List<String> clauseRefs, List<String> relevantAlgorithms, List<RuleCheck> ruleChecks, boolean ruleOverride,
		List<String> pendingParameters, long llmCallId) {

	public static final List<String> VALUES = List.of("符合", "部分符合", "不符合", "不适用");

	/** 表 1：D 不满足 → 不符合；D、A、K 都满足 → 符合；其余 → 部分符合。 */
	public static String fromDimensions(boolean d, Boolean a, Boolean k) {
		if (!d) {
			return "不符合";
		}
		return (Boolean.TRUE.equals(a) && Boolean.TRUE.equals(k)) ? "符合" : "部分符合";
	}

}
