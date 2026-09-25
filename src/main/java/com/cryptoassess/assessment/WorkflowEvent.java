package com.cryptoassess.assessment;

public enum WorkflowEvent {

	/** 录入或修改测评对象 */
	ADD_OBJECT,
	/** 启动分析，或从失败步骤继续、修改对象后重新分析 */
	START_ANALYSIS,
	ANALYSIS_SUCCEEDED,
	ANALYSIS_FAILED,
	/** 差距项全部人工确认 */
	CONFIRM,
	SCORE,
	GENERATE_REPORT

}
