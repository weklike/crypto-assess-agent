package com.cryptoassess.support;

/**
 * 按 judge-output.v2 格式拼假模型的判定输出：技术层面带 D/A/K，维度与判定一致（符合 = 全满足，
 * 部分符合 = K 不满足，不符合 = D 不满足）。
 */
public final class JudgeOutputs {

	private JudgeOutputs() {
	}

	public static String of(String judgment, String clauseRef) {
		String dims = switch (judgment) {
			case "符合" -> "\"d\":true,\"a\":true,\"k\":true";
			case "部分符合" -> "\"d\":true,\"a\":true,\"k\":false";
			case "不符合" -> "\"d\":false,\"a\":null,\"k\":null";
			default -> "\"d\":null,\"a\":null,\"k\":null";
		};
		return """
				{"applicability":"%s","judgment":"%s",%s,"k_module_level":null,"k_other_requirements_met":null,
				 "evidence":"描述中有相关措施","missing_info":[],"rationale":"理由","remediation":"",
				 "clause_refs":["%s"],"relevant_algorithms":[]}"""
			.formatted("不适用".equals(judgment) ? "不适用" : "适用", judgment, dims, clauseRef);
	}

}
