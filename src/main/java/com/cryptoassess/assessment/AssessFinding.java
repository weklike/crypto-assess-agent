package com.cryptoassess.assessment;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * 差距项：一个测评对象对一条测评指标的判定。
 *
 * @param source MODEL（模型判定）/ RULE（规则改判了某一维度）/ REVIEWER（人工修改）
 * @param dimD 密码使用有效性；技术层面才有，管理层面和不适用时为 null
 * @param dimA 密码算法/技术合规性
 * @param dimK 密钥管理安全
 * @param ra A 不满足时的修正参数；为 null 时需要复核人员补充
 * @param rk K 不满足时的修正参数
 */
public record AssessFinding(Long id, Long projectId, Long objectId, String clauseRef, String judgment,
		String evidence, @JsonRawValue String ruleHitsJson, String rationale, String remediation,
		@JsonRawValue String missingInfoJson, String source, Long llmCallId, boolean reviewed, String reviewerNote,
		Instant reviewedAt, Instant createdAt, Instant updatedAt, Boolean dimD, Boolean dimA, Boolean dimK, BigDecimal ra,
		BigDecimal rk, Integer moduleLevel) {

	public static final String SOURCE_MODEL = "MODEL";

	public static final String SOURCE_RULE = "RULE";

	public static final String SOURCE_REVIEWER = "REVIEWER";

}
