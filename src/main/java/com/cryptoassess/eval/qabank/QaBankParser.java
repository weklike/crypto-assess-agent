package com.cryptoassess.eval.qabank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cryptoassess.eval.dataset.DatasetIssue;

/**
 * 把 OFD 导出的题库文本转成结构化题目。支持的版式：
 * 题型小标题（一、单项选择题）、题号（1． / 1、 / 1.）、同一行或分行的选项（A．…）、答案行（答案：AB / 参考答案：正确）。
 * 版式与此不符的题目报行号，由人工修正文本后重跑，不猜测。
 */
public class QaBankParser {

	private static final Pattern SECTION = Pattern
		.compile("^[一二三四五六七八九十]+\\s*[、.．]\\s*(单项选择题|单选题|多项选择题|多选题|判断题).*$");

	private static final Pattern QUESTION = Pattern.compile("^(\\d{1,4})\\s*[.．、]\\s*(.+)$");

	private static final Pattern OPTION_LINE = Pattern.compile("^[A-H]\\s*[.．、]");

	private static final Pattern OPTION = Pattern
		.compile("([A-H])\\s*[.．、]\\s*(.*?)(?=\\s+[A-H]\\s*[.．、]|$)");

	private static final Pattern ANSWER = Pattern.compile("^(?:参考)?答案\\s*[:：]\\s*(.+)$");

	private static final List<String> TRUE_WORDS = List.of("正确", "对", "√", "✓", "T", "是");

	private static final List<String> FALSE_WORDS = List.of("错误", "错", "×", "✗", "F", "否");

	public record ParseResult(List<QaBankItem> items, List<DatasetIssue> issues) {
	}

	private static final class Draft {

		int line;

		String sectionType;

		StringBuilder stem = new StringBuilder();

		Map<String, String> options = new LinkedHashMap<>();

		String lastOption;

		String answerText;

	}

	public ParseResult parse(List<String> lines) {
		List<QaBankItem> items = new ArrayList<>();
		List<DatasetIssue> issues = new ArrayList<>();
		String sectionType = null;
		Draft draft = null;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (line.isEmpty()) {
				continue;
			}
			Matcher section = SECTION.matcher(line);
			if (section.matches()) {
				finish(draft, items, issues);
				draft = null;
				sectionType = sectionType(section.group(1));
				continue;
			}
			Matcher answer = ANSWER.matcher(line);
			if (answer.matches() && draft != null) {
				draft.answerText = answer.group(1).strip();
				continue;
			}
			if (OPTION_LINE.matcher(line).find() && draft != null) {
				Matcher option = OPTION.matcher(line);
				while (option.find()) {
					draft.options.put(option.group(1), option.group(2).strip());
					draft.lastOption = option.group(1);
				}
				continue;
			}
			Matcher question = QUESTION.matcher(line);
			if (question.matches()) {
				finish(draft, items, issues);
				draft = new Draft();
				draft.line = i + 1;
				draft.sectionType = sectionType;
				draft.stem.append(question.group(2).strip());
				continue;
			}
			if (draft == null) {
				continue;
			}
			// 折行：接到最后一个选项或题干后面
			if (draft.lastOption != null) {
				draft.options.merge(draft.lastOption, line, String::concat);
			}
			else {
				draft.stem.append(line);
			}
		}
		finish(draft, items, issues);
		List<QaBankItem> numbered = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			QaBankItem item = items.get(i);
			numbered.add(new QaBankItem(String.format("q%04d", i + 1), item.type(), item.stem(), item.options(),
					item.answer()));
		}
		return new ParseResult(numbered, issues);
	}

	private void finish(Draft draft, List<QaBankItem> items, List<DatasetIssue> issues) {
		if (draft == null) {
			return;
		}
		if (draft.answerText == null) {
			issues.add(new DatasetIssue(draft.line, "题目缺少答案行"));
			return;
		}
		boolean truth = TRUE_WORDS.contains(draft.answerText);
		boolean falsity = FALSE_WORDS.contains(draft.answerText);
		String type = draft.sectionType;
		if (type == null) {
			type = (truth || falsity) ? "judge" : (letters(draft.answerText).size() > 1 ? "multi" : "single");
		}
		Map<String, String> options = draft.options;
		List<String> answer;
		if ("judge".equals(type)) {
			if (!truth && !falsity) {
				issues.add(new DatasetIssue(draft.line, "判断题答案无法识别：" + draft.answerText));
				return;
			}
			options = new LinkedHashMap<>(Map.of("A", "正确", "B", "错误"));
			answer = List.of(truth ? "A" : "B");
		}
		else {
			answer = letters(draft.answerText);
			if (answer.isEmpty() || options.size() < 2) {
				issues.add(new DatasetIssue(draft.line, "选择题的选项或答案无法识别：" + draft.answerText));
				return;
			}
			for (String letter : answer) {
				if (!options.containsKey(letter)) {
					issues.add(new DatasetIssue(draft.line, "答案 " + letter + " 不在选项中"));
					return;
				}
			}
			if ("single".equals(type) && answer.size() != 1) {
				issues.add(new DatasetIssue(draft.line, "单选题答案不止一个：" + draft.answerText));
				return;
			}
		}
		items.add(new QaBankItem(null, type, draft.stem.toString(), Map.copyOf(options), answer));
	}

	private static List<String> letters(String text) {
		TreeSet<String> letters = new TreeSet<>();
		for (char c : text.toCharArray()) {
			if (c >= 'A' && c <= 'H') {
				letters.add(String.valueOf(c));
			}
			else if (!Character.isWhitespace(c) && ",，、;；".indexOf(c) < 0) {
				return List.of();
			}
		}
		return List.copyOf(letters);
	}

	private static String sectionType(String name) {
		return switch (name) {
			case "单项选择题", "单选题" -> "single";
			case "多项选择题", "多选题" -> "multi";
			default -> "judge";
		};
	}

}
