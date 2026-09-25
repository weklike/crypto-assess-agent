package com.cryptoassess.common.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

	@Test
	void examTemplateKeepsLiteralJsonBraces() {
		String text = PromptTemplates.render("qa-exam.v1",
				Map.of("typeName", "单选题", "context", "参考条款", "stem", "题干", "options", "A. 甲\nB. 乙"));

		assertThat(text).contains("{\"answer\": [\"A\"], \"citations\": [\"标准号#条款号\"]}").contains("题目：题干");
	}

	@Test
	void qaTemplateRendersClausesAndQuestion() {
		String text = PromptTemplates.render("qa-answer.v1", Map.of("clauses", "[F#1] 条款", "question", "问题？"));

		assertThat(text).contains("[F#1] 条款").contains("用户问题：问题？");
	}

}
