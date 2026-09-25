package com.cryptoassess.knowledge.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规范化标准 Markdown 的格式约定（开发计划 §5.1）。linter 和 T03 的解析器共用这里的定义。
 */
public final class NormalizedFormat {

	/** 标题行：井号、编号、标题。编号兼容附录（A、A.1、A.1.2）。 */
	static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(\\S+)(?:\\s+(.*))?$");

	static final Pattern CLAUSE_NO = Pattern.compile("^([0-9]+(\\.[0-9]+)*|[A-Z](\\.[0-9]+)*)$");

	static final Pattern COMMENT = Pattern.compile("^<!--(.*)-->$");

	static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

	public static final List<String> FRONT_MATTER_KEYS = List.of("doc_code", "title", "source_sha256", "normalized_by");

	public static final Set<String> LAYERS = Set.of("物理和环境", "网络和通信", "设备和计算", "应用和数据", "管理制度", "人员管理", "建设运行",
			"应急处置");

	public static final Set<String> CLAUSE_TYPES = Set.of("要求", "测评", "说明", "术语");

	public static final Set<String> LEVELS = Set.of("1", "2", "3", "4");

	private NormalizedFormat() {
	}

	/** 标题行解析结果；number 不一定合法，由调用方校验。 */
	record Heading(int level, String number, String title) {

		boolean hasValidNumber() {
			return CLAUSE_NO.matcher(this.number).matches();
		}

		/** 编号段数：6 → 1，6.2.1 → 3，A → 1，A.1 → 2 */
		int depth() {
			return this.number.split("\\.").length;
		}

		Optional<String> parentNumber() {
			int dot = this.number.lastIndexOf('.');
			return (dot < 0) ? Optional.empty() : Optional.of(this.number.substring(0, dot));
		}

		String lastSegment() {
			int dot = this.number.lastIndexOf('.');
			return (dot < 0) ? this.number : this.number.substring(dot + 1);
		}

		boolean isAppendix() {
			return Character.isLetter(this.number.charAt(0));
		}

	}

	static Optional<Heading> parseHeading(String line) {
		Matcher matcher = HEADING.matcher(line);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String title = (matcher.group(3) != null) ? matcher.group(3).strip() : "";
		return Optional.of(new Heading(matcher.group(1).length(), matcher.group(2), title));
	}

	static boolean isComment(String line) {
		return COMMENT.matcher(line.strip()).matches();
	}

	/**
	 * 解析 {@code <!-- layer: 网络和通信 | levels: 1,2,3,4 | type: 要求 -->}，返回键值对；格式错误返回空。
	 */
	static Optional<Map<String, String>> parseMetadata(String line) {
		Matcher matcher = COMMENT.matcher(line.strip());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String part : matcher.group(1).split("\\|")) {
			int colon = part.indexOf(':');
			if (colon < 0) {
				return Optional.empty();
			}
			values.put(part.substring(0, colon).strip(), part.substring(colon + 1).strip());
		}
		return Optional.of(values);
	}

	static List<String> splitLevels(String levels) {
		List<String> result = new ArrayList<>();
		Arrays.stream(levels.split(",")).map(String::strip).forEach(result::add);
		return result;
	}

}
