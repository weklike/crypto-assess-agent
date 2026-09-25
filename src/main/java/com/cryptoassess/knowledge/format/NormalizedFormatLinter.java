package com.cryptoassess.knowledge.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cryptoassess.knowledge.format.NormalizedFormat.Heading;

/**
 * 规范化标准文件的格式检查：front matter、编号唯一、层级连续、元数据齐全、叶子条款有正文。
 * 纯 Java，不依赖 Spring；命令行工具和 T03 的解析器都调用它。
 */
public class NormalizedFormatLinter {

	public List<LintIssue> lint(List<String> lines) {
		List<LintIssue> issues = new ArrayList<>();
		int bodyStart = lintFrontMatter(lines, issues);
		if (bodyStart < 0) {
			return issues;
		}
		lintBody(lines, bodyStart, issues);
		return issues;
	}

	/** 返回正文起始下标；没有 front matter 时返回 -1。 */
	private int lintFrontMatter(List<String> lines, List<LintIssue> issues) {
		if (lines.isEmpty() || !"---".equals(lines.get(0).strip())) {
			issues.add(new LintIssue(1, "缺少 front matter（文件应以 --- 开头）"));
			return -1;
		}
		Map<String, String> values = new HashMap<>();
		int end = -1;
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if ("---".equals(line)) {
				end = i;
				break;
			}
			int colon = line.indexOf(':');
			if (colon < 0) {
				issues.add(new LintIssue(i + 1, "front matter 行应为 key: value"));
				continue;
			}
			values.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
		}
		if (end < 0) {
			issues.add(new LintIssue(1, "front matter 没有结束的 ---"));
			return -1;
		}
		for (String key : NormalizedFormat.FRONT_MATTER_KEYS) {
			if (values.getOrDefault(key, "").isBlank()) {
				issues.add(new LintIssue(1, "front matter 缺少 " + key));
			}
		}
		String sha = values.get("source_sha256");
		if (sha != null && !sha.isBlank() && !NormalizedFormat.SHA256.matcher(sha).matches()) {
			issues.add(new LintIssue(1, "source_sha256 应为 64 位小写十六进制"));
		}
		return end + 1;
	}

	private void lintBody(List<String> lines, int start, List<LintIssue> issues) {
		Set<String> seen = new HashSet<>();
		// 每个父编号（顶层用 ""）下最后出现的子编号，用于检查同级连续
		Map<String, Heading> lastChild = new HashMap<>();
		Heading current = null;
		boolean expectMetadata = false;
		Set<String> parents = new HashSet<>();
		List<Heading> headings = new ArrayList<>();
		List<Integer> headingLines = new ArrayList<>();
		List<Boolean> bodies = new ArrayList<>();

		for (int i = start; i < lines.size(); i++) {
			int lineNo = i + 1;
			String line = lines.get(i).strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("#")) {
				Optional<Heading> parsed = NormalizedFormat.parseHeading(line);
				if (parsed.isEmpty() || !parsed.get().hasValidNumber() || parsed.get().title().isEmpty()) {
					issues.add(new LintIssue(lineNo, "标题行缺少合法编号或标题：" + abbreviate(line)));
					expectMetadata = false;
					continue;
				}
				Heading heading = parsed.get();
				checkHeading(heading, lineNo, seen, lastChild, issues);
				heading.parentNumber().ifPresent(parents::add);
				headings.add(heading);
				headingLines.add(lineNo);
				bodies.add(false);
				current = heading;
				expectMetadata = true;
				continue;
			}
			if (line.startsWith("|")) {
				issues.add(new LintIssue(lineNo, "表格需按“一行一条”转成文本，不能保留 Markdown 表格"));
				continue;
			}
			if (NormalizedFormat.isComment(line)) {
				if (!expectMetadata || current == null) {
					issues.add(new LintIssue(lineNo, "元数据注释必须紧跟在条款标题之后"));
				}
				else {
					checkMetadata(line, lineNo, issues);
				}
				expectMetadata = false;
				continue;
			}
			expectMetadata = false;
			if (current == null) {
				issues.add(new LintIssue(lineNo, "正文出现在第一个标题之前"));
				continue;
			}
			bodies.set(bodies.size() - 1, true);
		}

		for (int i = 0; i < headings.size(); i++) {
			Heading heading = headings.get(i);
			if (!parents.contains(heading.number()) && !bodies.get(i)) {
				issues.add(new LintIssue(headingLines.get(i), "条款 " + heading.number() + " 没有子条款也没有正文"));
			}
		}
		issues.sort((a, b) -> Integer.compare(a.line(), b.line()));
	}

	private void checkHeading(Heading heading, int lineNo, Set<String> seen, Map<String, Heading> lastChild,
			List<LintIssue> issues) {
		String number = heading.number();
		if (!seen.add(number)) {
			issues.add(new LintIssue(lineNo, "条款编号 " + number + " 重复"));
			return;
		}
		if (heading.level() != heading.depth()) {
			issues.add(new LintIssue(lineNo, "标题层级 " + heading.level() + " 与编号 " + number + " 的段数 " + heading.depth() + " 不符"));
		}
		String parent = heading.parentNumber().orElse("");
		if (!parent.isEmpty() && !seen.contains(parent)) {
			issues.add(new LintIssue(lineNo, "条款 " + number + " 的父条款 " + parent + " 不存在或出现在它之后"));
			return;
		}
		Heading previous = lastChild.put(parent, heading);
		if (!isContinuous(previous, heading)) {
			issues.add(new LintIssue(lineNo,
					"同级编号不连续：" + ((previous == null) ? "（无）" : previous.number()) + " 之后是 " + number));
		}
	}

	/**
	 * 子条款从 1 开始逐一递增；顶层章节只要求递增（规范化时允许省略前几章），附录字母逐一递增且排在数字章节之后。
	 */
	private boolean isContinuous(Heading previous, Heading heading) {
		boolean topLevel = heading.parentNumber().isEmpty();
		String last = heading.lastSegment();
		if (topLevel && heading.isAppendix()) {
			if (previous == null || !previous.isAppendix()) {
				return "A".equals(last);
			}
			return last.charAt(0) == previous.lastSegment().charAt(0) + 1;
		}
		if (topLevel) {
			return previous == null || (!previous.isAppendix()
					&& Integer.parseInt(last) > Integer.parseInt(previous.lastSegment()));
		}
		int value = Integer.parseInt(last);
		return (previous == null) ? value == 1 : value == Integer.parseInt(previous.lastSegment()) + 1;
	}

	private void checkMetadata(String line, int lineNo, List<LintIssue> issues) {
		Optional<Map<String, String>> parsed = NormalizedFormat.parseMetadata(line);
		if (parsed.isEmpty()) {
			issues.add(new LintIssue(lineNo, "元数据格式应为 <!-- layer: … | levels: … | type: … -->"));
			return;
		}
		Map<String, String> values = parsed.get();
		for (String key : List.of("layer", "levels", "type")) {
			if (values.getOrDefault(key, "").isBlank()) {
				issues.add(new LintIssue(lineNo, "元数据缺少 " + key));
			}
		}
		String layer = values.get("layer");
		if (layer != null && !layer.isBlank() && !NormalizedFormat.LAYERS.contains(layer)) {
			issues.add(new LintIssue(lineNo, "layer 取值非法：" + layer + "，应为 " + NormalizedFormat.LAYERS));
		}
		String levels = values.get("levels");
		if (levels != null && !levels.isBlank()) {
			List<String> parts = NormalizedFormat.splitLevels(levels);
			boolean valid = parts.stream().allMatch(NormalizedFormat.LEVELS::contains)
					&& parts.equals(parts.stream().distinct().sorted().toList());
			if (!valid) {
				issues.add(new LintIssue(lineNo, "levels 取值非法：" + levels + "，应为 1-4 的升序、不重复列表"));
			}
		}
		String type = values.get("type");
		if (type != null && !type.isBlank() && !NormalizedFormat.CLAUSE_TYPES.contains(type)) {
			issues.add(new LintIssue(lineNo, "type 取值非法：" + type + "，应为 " + NormalizedFormat.CLAUSE_TYPES));
		}
	}

	private static String abbreviate(String line) {
		return (line.length() > 40) ? line.substring(0, 40) + "…" : line;
	}

}
