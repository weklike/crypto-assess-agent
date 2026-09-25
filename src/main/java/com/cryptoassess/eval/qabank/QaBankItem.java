package com.cryptoassess.eval.qabank;

import java.util.List;
import java.util.Map;

/**
 * 题库中的一道题（data/private/qa_bank.jsonl 的一行）。题目原文只存在 data/private/，不进 git。
 *
 * @param type single / multi / judge
 * @param options 选项字母 → 选项文本；判断题固定为 A 正确、B 错误
 * @param answer 正确选项字母，按字母排序
 */
public record QaBankItem(String id, String type, String stem, Map<String, String> options, List<String> answer) {

	public static final List<String> TYPES = List.of("single", "multi", "judge");

}
