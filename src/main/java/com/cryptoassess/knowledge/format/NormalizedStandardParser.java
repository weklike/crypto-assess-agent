package com.cryptoassess.knowledge.format;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cryptoassess.knowledge.format.NormalizedFormat.Heading;

/**
 * 把规范化 Markdown 解析成条款。先跑 {@link NormalizedFormatLinter}，有任何问题就整份拒绝，
 * 因此这里可以假定格式正确。纯 Java，不依赖 Spring。
 */
public class NormalizedStandardParser {

	public static final String PARSER_VERSION = "1";

	private static final String PATH_SEPARATOR = " > ";

	private final NormalizedFormatLinter linter = new NormalizedFormatLinter();

	public ParsedStandard parse(List<String> rawLines) {
		List<String> lines = rawLines.stream().map(NormalizedStandardParser::stripLineEnding).toList();
		List<LintIssue> issues = this.linter.lint(lines);
		if (!issues.isEmpty()) {
			throw new NormalizedFormatException(issues);
		}
		Map<String, String> frontMatter = new HashMap<>();
		int i = 1;
		for (; !"---".equals(lines.get(i).strip()); i++) {
			String line = lines.get(i);
			int colon = line.indexOf(':');
			frontMatter.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
		}
		String docCode = frontMatter.get("doc_code");
		List<ClauseRecord> clauses = parseClauses(docCode, lines.subList(i + 1, lines.size()));
		return new ParsedStandard(docCode, frontMatter.get("title"), frontMatter.get("source_sha256"),
				frontMatter.get("normalized_by"), sha256(String.join("\n", lines)), clauses);
	}

	private List<ClauseRecord> parseClauses(String docCode, List<String> lines) {
		List<ClauseRecord> clauses = new ArrayList<>();
		// 编号 → “编号 标题”，用来拼祖先路径
		Map<String, String> labels = new HashMap<>();
		Heading heading = null;
		Map<String, String> metadata = Map.of();
		List<String> body = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.strip();
			if (line.startsWith("#")) {
				if (heading != null) {
					clauses.add(toRecord(docCode, heading, metadata, body, labels, clauses.size() + 1));
				}
				heading = NormalizedFormat.parseHeading(line).orElseThrow();
				labels.put(heading.number(), heading.number() + " " + heading.title());
				metadata = Map.of();
				body = new ArrayList<>();
			}
			else if (NormalizedFormat.isComment(line)) {
				metadata = NormalizedFormat.parseMetadata(line).orElseThrow();
			}
			else if (!line.isEmpty()) {
				body.add(line);
			}
		}
		if (heading != null) {
			clauses.add(toRecord(docCode, heading, metadata, body, labels, clauses.size() + 1));
		}
		return clauses;
	}

	private ClauseRecord toRecord(String docCode, Heading heading, Map<String, String> metadata, List<String> body,
			Map<String, String> labels, int ordinal) {
		List<String> ancestors = new ArrayList<>();
		String parent = heading.parentNumber().orElse(null);
		for (String p = parent; p != null; p = parentOf(p)) {
			ancestors.add(0, labels.get(p));
		}
		String text = String.join("\n", body);
		return new ClauseRecord(docCode + "#" + heading.number(), heading.number(), parent,
				String.join(PATH_SEPARATOR, ancestors), heading.title(), text, metadata.get("layer"),
				metadata.get("levels") == null ? null : String.join(",", NormalizedFormat.splitLevels(metadata.get("levels"))),
				metadata.get("type"), ordinal, sha256(text));
	}

	private static String parentOf(String number) {
		int dot = number.lastIndexOf('.');
		return (dot < 0) ? null : number.substring(0, dot);
	}

	private static String stripLineEnding(String line) {
		return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
	}

	static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
