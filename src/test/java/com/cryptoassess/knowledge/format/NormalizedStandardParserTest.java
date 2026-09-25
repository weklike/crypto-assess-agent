package com.cryptoassess.knowledge.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NormalizedStandardParserTest {

	private static ParsedStandard fixture;

	private static Map<String, ClauseRecord> byNo;

	@BeforeAll
	static void parseFixture() throws IOException {
		fixture = new NormalizedStandardParser()
			.parse(Files.readAllLines(Path.of("data/fixtures/mock-standard.md")));
		byNo = fixture.clauses().stream().collect(Collectors.toMap(ClauseRecord::clauseNo, Function.identity()));
	}

	@Test
	void readsFrontMatter() {
		assertThat(fixture.docCode()).isEqualTo("FIX/T 0001-2026");
		assertThat(fixture.title()).isEqualTo("仿标准 信息系统密码应用测试要求");
		assertThat(fixture.sourceSha256()).hasSize(64);
		assertThat(fixture.normalizedSha256()).hasSize(64);
	}

	@Test
	void everyHeadingBecomesAClauseInDocumentOrder() {
		assertThat(fixture.clauses()).hasSize(36);
		assertThat(fixture.clauses()).extracting(ClauseRecord::ordinal)
			.containsExactlyElementsOf(Stream.iterate(1, i -> i + 1).limit(36).toList());
		assertThat(fixture.clauses().get(0).clauseNo()).isEqualTo("4");
		assertThat(fixture.clauses().get(35).clauseNo()).isEqualTo("A.1");
	}

	static Stream<Arguments> expectedClauses() {
		return Stream.of(
				// clauseNo, parentNo, path, title, layer, levels, type
				Arguments.of("4", null, "", "通用要求", null, null, null),
				Arguments.of("4.1", "4", "4 通用要求", "密码算法", "应用和数据", "1,2,3,4", "要求"),
				Arguments.of("5.1.2", "5.1", "5 技术要求 > 5.1 物理和环境", "门禁记录数据完整性", "物理和环境", "2,3,4", "要求"),
				Arguments.of("5.2.5", "5.2", "5 技术要求 > 5.2 网络和通信", "安全接入认证", "网络和通信", "3,4", "要求"),
				Arguments.of("6.4.1", "6.4", "6 管理要求 > 6.4 应急处置", "应急预案", "应急处置", "1,2,3,4", "要求"),
				Arguments.of("A", null, "", "术语示例", null, null, null),
				Arguments.of("A.1", "A", "A 术语示例", "消息鉴别码", "应用和数据", "1,2,3,4", "术语"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("expectedClauses")
	void parsesNumberPathAndMetadata(String clauseNo, String parentNo, String path, String title, String layer,
			String levels, String type) {
		ClauseRecord clause = byNo.get(clauseNo);

		assertThat(clause).isNotNull();
		assertThat(clause.clauseRef()).isEqualTo("FIX/T 0001-2026#" + clauseNo);
		assertThat(clause.parentNo()).isEqualTo(parentNo);
		assertThat(clause.path()).isEqualTo(path);
		assertThat(clause.title()).isEqualTo(title);
		assertThat(clause.layer()).isEqualTo(layer);
		assertThat(clause.levels()).isEqualTo(levels);
		assertThat(clause.clauseType()).isEqualTo(type);
	}

	@Test
	void bodyExcludesMetadataAndStopsAtNextHeading() {
		ClauseRecord clause = byNo.get("5.2.3");

		assertThat(clause.body()).startsWith("重要数据在网络中传输时应加密").doesNotContain("<!--").doesNotContain("#");
		assertThat(clause.bodySha256()).hasSize(64);
		assertThat(byNo.get("5").body()).isEmpty();
	}

	@Test
	void normalizedShaIgnoresLineEndingStyle() {
		List<String> lf = List.of("---", "doc_code: FIX/T 0001-2026", "title: t", "source_sha256: " + "0".repeat(64),
				"normalized_by: x", "---", "# 5 章", "正文。");
		ParsedStandard a = new NormalizedStandardParser().parse(lf);
		ParsedStandard b = new NormalizedStandardParser().parse(lf.stream().map(l -> l + "\r").toList());

		assertThat(a.normalizedSha256()).isEqualTo(b.normalizedSha256());
	}

	@Test
	void invalidDocumentFailsWithLineNumbers() {
		List<String> broken = List.of("---", "doc_code: FIX/T 0001-2026", "title: t", "source_sha256: " + "0".repeat(64),
				"normalized_by: x", "---", "# 5 章", "正文。", "## 5.2 跳号", "正文。");

		assertThatThrownBy(() -> new NormalizedStandardParser().parse(broken))
			.isInstanceOf(NormalizedFormatException.class)
			.satisfies(ex -> assertThat(((NormalizedFormatException) ex).issues()).extracting(LintIssue::line)
				.containsExactly(9));
	}

}
