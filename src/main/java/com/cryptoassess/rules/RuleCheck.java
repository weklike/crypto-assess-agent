package com.cryptoassess.rules;

import java.util.List;

/**
 * 一次算法规则检查的结果。
 *
 * @param input 原始写法
 * @param name 规范化名称；认不出时为 UNKNOWN
 * @param mode 工作模式（如 CBC、GCM），没有时为 null
 * @param keyBits 写法中带的密钥位数，没有时为 null
 * @param ruleVersion 规则表版本，如 algorithms.v1
 * @param securityBits 该写法对应的安全强度（比特），用于量化评估的 Ra；规则表未给出时为 null
 */
public record RuleCheck(String input, String name, String category, AlgorithmStatus status, String basis,
		List<String> clauseRefs, String ruleVersion, String mode, Integer keyBits, Integer securityBits) {

}
