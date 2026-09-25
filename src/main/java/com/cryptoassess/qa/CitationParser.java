package com.cryptoassess.qa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型输出中解析 [标准号#条款号] 形式的引用，兼容全角括号、多余空格、长短破折号、同一括号内多个引用。
 * 输出规范化后的 clause_ref，去重并保持首次出现的顺序。
 */
public final class CitationParser {

	private static final Pattern BRACKET = Pattern.compile("[\\[【［]([^\\[\\]【】［］]{1,200})[\\]】］]");

	/** 标准号：字母前缀（可带 /T 等）+ 数字 + 年份；条款号：数字编号或附录编号 */
	private static final Pattern REF = Pattern.compile(
			"^([A-Z]{1,4}(?:\\s*/\\s*[A-Z]{1,2})?)\\s*(\\d+(?:\\.\\d+)?)\\s*[-—–－]\\s*(\\d{4})\\s*[#＃]\\s*([0-9]+(?:\\.[0-9]+)*|[A-Z](?:\\.[0-9]+)*)$");

	private static final Pattern SEPARATOR = Pattern.compile("[;；,，、]");

	private CitationParser() {
	}

	public static List<String> parse(String text) {
		Set<String> refs = new LinkedHashSet<>();
		if (text == null) {
			return List.of();
		}
		Matcher bracket = BRACKET.matcher(text);
		while (bracket.find()) {
			for (String part : SEPARATOR.split(bracket.group(1))) {
				Matcher ref = REF.matcher(part.strip());
				if (ref.matches()) {
					String prefix = ref.group(1).replaceAll("\\s+", "");
					refs.add(prefix + " " + ref.group(2) + "-" + ref.group(3) + "#" + ref.group(4));
				}
			}
		}
		return new ArrayList<>(refs);
	}

}
