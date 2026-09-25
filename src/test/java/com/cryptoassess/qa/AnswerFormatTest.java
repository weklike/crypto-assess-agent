package com.cryptoassess.qa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerFormatTest {

	@Test
	void plainTextWithNumberedPointsAndCitationsPasses() {
		String answer = """
				1. 重要数据在网络中传输时应加密 [FIX/T 0001-2026#5.2.3]。
				2. 通信双方应先进行身份鉴别【FIX/T 0001-2026#5.2.1】。
				回答仅供辅助自查。""";

		assertThat(AnswerFormat.markdownIssues(answer)).isEmpty();
	}

	@Test
	void markdownMarkersAreReportedByKind() {
		String answer = """
				## 结论
				- **传输加密**：应使用 SM4 [FIX/T 0001-2026#5.2.3]
				* 另见 `SSL VPN`
				> 引用块
				| 项 | 值 |
				|---|---|
				详见 [说明](https://example.com)
				```
				代码
				```""";

		assertThat(AnswerFormat.markdownIssues(answer)).containsExactly("heading", "bold", "bullet", "code",
				"quote", "table", "link");
	}

	@Test
	void citationBracketsAreNotMistakenForLinks() {
		assertThat(AnswerFormat.markdownIssues("依据 [GB/T 39786-2021#8.1] (第三级)。")).isEmpty();
	}

}
