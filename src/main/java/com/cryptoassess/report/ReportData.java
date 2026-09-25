package com.cryptoassess.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 报告内容，全部来自数据库（项目、差距项、得分、模型调用记录）；导出时不调用模型。
 *
 * @param total 总分；不出总分时为 null
 * @param totalNote 不出总分的原因（如某组全部不适用）
 * @param findingCount 差距项总数（含符合、不适用）
 */
public record ReportData(String systemName, String projectName, int level, Instant generatedAt, BigDecimal total,
		String totalNote,
		List<LayerScore> layers, List<Gap> gaps, int findingCount, List<String> models, List<String> promptVersions,
		String algorithmRuleVersion, boolean algorithmRulesReviewed, String scoringRuleVersion,
		boolean scoringRulesReviewed) {

	public record LayerScore(String layer, BigDecimal score, BigDecimal weight) {
	}

	/**
	 * @param dimensions 技术层面的 D/A/K 与 Ra、Rk，如“D√ A× K√ Ra=0.5”；管理层面为 null
	 * @param remediation 整改建议，判定步骤生成并落库
	 * @param reviewerNote 测评人员复核备注
	 */
	public record Gap(String objectName, String layer, String clauseRef, String clauseTitle, String judgment,
			String dimensions, String evidence, String remediation, String reviewerNote) {
	}

}
