package com.cryptoassess.knowledge.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PdfDraftConverterTest {

	@Test
	void turnsNumberedLinesIntoHeadingsAndDropsPageNoise() {
		List<String> extracted = List.of(
				"FIX/T 0001—2026",
				"5 总体要求",
				"本章给出通用的说明，正文在排版时",
				"被折成了两行。",
				"5.1 基本原则",
				"宜采用密码技术保护重要数据。",
				"3",
				"\f FIX/T 0001—2026",
				"5.1.1 细则",
				"a) 第一项；",
				"b) 第二项。",
				"附录 A",
				"A.1 示例条",
				"附录正文。");

		String draft = new PdfDraftConverter().convert(extracted, "FIX/T 0001-2026", "仿标准", "ab".repeat(32));

		assertThat(draft).startsWith("---\ndoc_code: FIX/T 0001-2026\n");
		assertThat(draft).contains("source_sha256: " + "ab".repeat(32));
		assertThat(draft).contains("# 5 总体要求\n本章给出通用的说明，正文在排版时被折成了两行。\n");
		assertThat(draft).contains("## 5.1 基本原则\n");
		assertThat(draft).contains("### 5.1.1 细则\na) 第一项；\nb) 第二项。\n");
		assertThat(draft).contains("## A.1 示例条\n");
		assertThat(draft).doesNotContain("0001—2026\n5").doesNotContain("\n3\n");
	}

	@Test
	void doesNotTreatNumbersInsideSentencesAsHeadings() {
		String draft = new PdfDraftConverter().convert(
				List.of("5 总体要求", "3 个以上的测评对象应当逐一核查，这句话很长不是标题。"), "FIX/T 0001-2026", "仿标准",
				"ab".repeat(32));

		assertThat(draft).contains("# 5 总体要求\n3 个以上的测评对象应当逐一核查，这句话很长不是标题。\n");
	}

}
