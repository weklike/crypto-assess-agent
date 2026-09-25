package com.cryptoassess.knowledge.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NormalizedFormatLinterTest {

	private static final String FRONT_MATTER = """
			---
			doc_code: FIX/T 0001-2026
			title: 仿标准 测试用密码应用要求
			source_sha256: 0000000000000000000000000000000000000000000000000000000000000000
			normalized_by: tester
			---
			""";

	private final NormalizedFormatLinter linter = new NormalizedFormatLinter();

	@Test
	void fixtureFilesPassLint() throws IOException {
		try (Stream<Path> files = Files.list(Path.of("data/fixtures"))) {
			List<Path> markdown = files.filter(p -> p.toString().endsWith(".md")).toList();
			assertThat(markdown).isNotEmpty();
			for (Path file : markdown) {
				assertThat(linter.lint(Files.readAllLines(file))).as(file.toString()).isEmpty();
			}
		}
	}

	@Test
	void validMinimalDocumentHasNoIssues() {
		String doc = FRONT_MATTER + """
				# 5 总体要求
				本章为通用说明。
				## 5.1 基本原则
				<!-- layer: 应用和数据 | levels: 1,2,3,4 | type: 要求 -->
				宜采用密码技术。
				## 5.2 其他原则
				正文。
				# A 附录示例
				## A.1 示例条
				附录正文。
				""";

		assertThat(linter.lint(doc.lines().toList())).isEmpty();
	}

	static Stream<Arguments> brokenDocuments() {
		// 行号从 1 开始；front matter 占 6 行，正文从第 7 行开始
		return Stream.of(
				Arguments.of("重复编号", """
						# 5 总体要求
						正文。
						# 5 又一个
						正文。
						""", 9, "重复"),
				Arguments.of("标题层级与编号段数不符", """
						# 5 总体要求
						正文。
						### 5.1 基本原则
						正文。
						""", 9, "层级"),
				Arguments.of("父条款不存在", """
						# 5 总体要求
						正文。
						## 6.1 跳到别的章
						正文。
						""", 9, "父条款"),
				Arguments.of("同级编号不连续", """
						# 5 总体要求
						## 5.1 一
						正文。
						## 5.3 三
						正文。
						""", 10, "不连续"),
				Arguments.of("元数据缺字段", """
						# 5 总体要求
						<!-- layer: 应用和数据 | levels: 3 -->
						正文。
						""", 8, "type"),
				Arguments.of("安全层面取值非法", """
						# 5 总体要求
						<!-- layer: 应用安全 | levels: 3 | type: 要求 -->
						正文。
						""", 8, "layer"),
				Arguments.of("等级取值非法", """
						# 5 总体要求
						<!-- layer: 应用和数据 | levels: 3,5 | type: 要求 -->
						正文。
						""", 8, "levels"),
				Arguments.of("元数据不紧跟标题", """
						# 5 总体要求
						正文。
						<!-- layer: 应用和数据 | levels: 3 | type: 要求 -->
						""", 9, "紧跟"),
				Arguments.of("叶子条款没有正文", """
						# 5 总体要求
						## 5.1 空条款
						## 5.2 有正文
						正文。
						""", 8, "正文"),
				Arguments.of("标题没有合法编号", """
						# 前言
						正文。
						""", 7, "编号"),
				Arguments.of("表格未转成文本", """
						# 5 总体要求
						| 项 | 值 |
						""", 8, "表格"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("brokenDocuments")
	void reportsLineLevelErrors(String name, String body, int expectedLine, String messagePart) {
		List<LintIssue> issues = linter.lint((FRONT_MATTER + body).lines().toList());

		assertThat(issues).as(name)
			.anySatisfy(issue -> {
				assertThat(issue.line()).isEqualTo(expectedLine);
				assertThat(issue.message()).contains(messagePart);
			});
	}

	@Test
	void missingFrontMatterIsReportedOnFirstLine() {
		List<LintIssue> issues = linter.lint(List.of("# 5 总体要求", "正文。"));

		assertThat(issues).first().satisfies(issue -> {
			assertThat(issue.line()).isEqualTo(1);
			assertThat(issue.message()).contains("front matter");
		});
	}

	@Test
	void frontMatterRequiresAllKeysAndValidSha() {
		String doc = """
				---
				doc_code: FIX/T 0001-2026
				source_sha256: abc
				---
				# 5 总体要求
				正文。
				""";

		List<LintIssue> issues = linter.lint(doc.lines().toList());

		assertThat(issues).extracting(LintIssue::message)
			.anyMatch(m -> m.contains("title"))
			.anyMatch(m -> m.contains("normalized_by"))
			.anyMatch(m -> m.contains("source_sha256"));
	}

}
