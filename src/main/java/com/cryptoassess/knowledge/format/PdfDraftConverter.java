package com.cryptoassess.knowledge.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 pdftotext 的输出转成规范化 Markdown 初稿：识别编号标题、合并被排版折断的行、去掉页眉页码。
 * 只是初稿，表格、跨页条款、元数据注释都要人工核对补全，然后用 {@link NormalizedFormatLinter} 检查。
 */
public class PdfDraftConverter {

	private static final Pattern NUMBERED = Pattern.compile("^([0-9]+(?:\\.[0-9]+)*|[A-Z](?:\\.[0-9]+)+)\\s+(\\S.*)$");

	private static final Pattern APPENDIX = Pattern.compile("^附\\s*录\\s*([A-Z])\\b.*$");

	private static final Pattern PAGE_NUMBER = Pattern.compile("^([0-9]{1,3}|[IVXLC]{1,6})$");

	private static final Pattern LIST_ITEM = Pattern.compile("^([a-z]\\)|[0-9]{1,2}\\)|——|—|注[0-9]*[:：]|示例[0-9]*[:：]).*");

	/** 标题一般很短、不以句读结尾；超过这个长度的编号行按正文处理 */
	private static final int MAX_TITLE_LENGTH = 30;

	public String convert(List<String> extractedLines, String docCode, String title, String sourceSha256) {
		StringBuilder out = new StringBuilder();
		out.append("---\n")
			.append("doc_code: ").append(docCode).append('\n')
			.append("title: ").append(title).append('\n')
			.append("source_sha256: ").append(sourceSha256).append('\n')
			.append("normalized_by: TODO\n")
			.append("---\n");
		String headerKey = compact(docCode);
		List<String> paragraph = new ArrayList<>();
		for (String raw : extractedLines) {
			String line = raw.replace("\f", "").strip();
			if (line.isEmpty()) {
				flush(paragraph, out);
				continue;
			}
			if (PAGE_NUMBER.matcher(line).matches() || compact(line).equals(headerKey)) {
				continue;
			}
			Matcher appendix = APPENDIX.matcher(line);
			if (appendix.matches()) {
				flush(paragraph, out);
				out.append("# ").append(appendix.group(1)).append(" 附录").append(appendix.group(1)).append('\n');
				continue;
			}
			Matcher numbered = NUMBERED.matcher(line);
			if (numbered.matches() && looksLikeTitle(numbered.group(2))) {
				flush(paragraph, out);
				String number = numbered.group(1);
				out.append("#".repeat(Math.min(6, number.split("\\.").length)))
					.append(' ').append(number).append(' ').append(numbered.group(2).strip()).append('\n');
				continue;
			}
			if (LIST_ITEM.matcher(line).matches()) {
				flush(paragraph, out);
			}
			paragraph.add(line);
			if (endsSentence(line)) {
				flush(paragraph, out);
			}
		}
		flush(paragraph, out);
		return out.toString();
	}

	private static boolean looksLikeTitle(String text) {
		String stripped = text.strip();
		return stripped.length() <= MAX_TITLE_LENGTH && !endsSentence(stripped) && !stripped.contains("，");
	}

	private static boolean endsSentence(String line) {
		char last = line.charAt(line.length() - 1);
		return "。；：！？;:".indexOf(last) >= 0;
	}

	private static void flush(List<String> paragraph, StringBuilder out) {
		if (paragraph.isEmpty()) {
			return;
		}
		StringBuilder joined = new StringBuilder(paragraph.get(0));
		for (int i = 1; i < paragraph.size(); i++) {
			String next = paragraph.get(i);
			char left = joined.charAt(joined.length() - 1);
			// 只有英文单词或数字跨行时补空格，中文直接拼接
			if (isAsciiWord(left) && isAsciiWord(next.charAt(0))) {
				joined.append(' ');
			}
			joined.append(next);
		}
		out.append(joined).append('\n');
		paragraph.clear();
	}

	private static boolean isAsciiWord(char c) {
		return c < 128 && Character.isLetterOrDigit(c);
	}

	private static String compact(String text) {
		return text.replace('—', '-').replace('－', '-').replaceAll("\\s+", "");
	}

}
