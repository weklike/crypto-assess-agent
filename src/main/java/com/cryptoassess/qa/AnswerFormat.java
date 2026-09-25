package com.cryptoassess.qa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 回答格式校验：提示词要求纯文本（可用“1. ”编号分点），这里在整段回答生成后检查是否混入 Markdown 标记，
 * 结果随 done 事件返回，页面据此提示。只报告、不改写回答内容。
 */
final class AnswerFormat {

	/** 按固定顺序检查，返回的问题种类顺序稳定。条款引用的方括号后面不跟“(url)”，不会被当成链接。 */
	private static final Map<String, Pattern> CHECKS = new LinkedHashMap<>();

	static {
		CHECKS.put("heading", Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s"));
		CHECKS.put("bold", Pattern.compile("\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__"));
		CHECKS.put("bullet", Pattern.compile("(?m)^\\s*[-*+]\\s+\\S"));
		CHECKS.put("code", Pattern.compile("`"));
		CHECKS.put("quote", Pattern.compile("(?m)^\\s*>\\s?"));
		CHECKS.put("table", Pattern.compile("(?m)^\\s*\\|.*\\|\\s*$"));
		CHECKS.put("link", Pattern.compile("\\[[^\\]\\n]+\\]\\([^)\\s]+\\)"));
	}

	private AnswerFormat() {
	}

	static List<String> markdownIssues(String text) {
		List<String> issues = new ArrayList<>();
		if (text == null) {
			return issues;
		}
		CHECKS.forEach((kind, pattern) -> {
			if (pattern.matcher(text).find()) {
				issues.add(kind);
			}
		});
		return issues;
	}

}
