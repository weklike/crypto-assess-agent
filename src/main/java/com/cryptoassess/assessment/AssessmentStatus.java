package com.cryptoassess.assessment;

/**
 * 评估项目状态（开发计划 §7.4）。状态只存在 assess_project.status 一列，转换只能经由 {@link AssessmentWorkflow}。
 */
public enum AssessmentStatus {

	DRAFT, READY, ANALYZING, REVIEW, FAILED, CONFIRMED, SCORED, REPORTED

}
