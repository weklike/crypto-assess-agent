package com.cryptoassess.eval.qabank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cryptoassess.eval.qabank.QaBankParser.ParseResult;
import org.junit.jupiter.api.Test;

/**
 * 样例文本是自拟的“仿题”，不是考核题库原文。
 */
class QaBankParserTest {

	private static final String SAMPLE = """
			一、单项选择题
			1．仿标准中，机房门禁宜采用（  ）确认人员身份。
			A．密码技术 B．口头询问
			C．纸质登记 D．不做要求
			答案：A
			2、下列哪项不属于仿标准的技术层面？
			A. 物理和环境
			B. 网络和通信
			C. 人员管理
			D. 应用和数据
			参考答案：C
			二、多项选择题
			3．仿标准要求保护重要数据的哪些属性？（  ）
			A．机密性 B．完整性 C．美观性 D．真实性
			答案：ABD
			三、判断题
			4．仿标准允许把加密密钥与密文存放在同一位置。（  ）
			答案：错误
			5．仿标准要求制定应急预案。
			答案：√
			6．这一题缺了答案
			A．甲 B．乙
			""";

	@Test
	void parsesSectionsOptionsAndAnswers() {
		ParseResult result = new QaBankParser().parse(SAMPLE.lines().toList());

		List<QaBankItem> items = result.items();
		assertThat(items).hasSize(5);
		QaBankItem first = items.get(0);
		assertThat(first.id()).isEqualTo("q0001");
		assertThat(first.type()).isEqualTo("single");
		assertThat(first.stem()).isEqualTo("仿标准中，机房门禁宜采用（  ）确认人员身份。");
		assertThat(first.options()).containsEntry("A", "密码技术").containsEntry("D", "不做要求").hasSize(4);
		assertThat(first.answer()).containsExactly("A");
		assertThat(items.get(1).options()).containsEntry("C", "人员管理");
		assertThat(items.get(1).answer()).containsExactly("C");
		assertThat(items.get(2).type()).isEqualTo("multi");
		assertThat(items.get(2).answer()).containsExactly("A", "B", "D");
		assertThat(items.get(3).type()).isEqualTo("judge");
		assertThat(items.get(3).options()).containsEntry("A", "正确").containsEntry("B", "错误");
		assertThat(items.get(3).answer()).containsExactly("B");
		assertThat(items.get(4).answer()).containsExactly("A");
	}

	@Test
	void reportsQuestionsWithoutAnswerByLine() {
		ParseResult result = new QaBankParser().parse(SAMPLE.lines().toList());

		assertThat(result.issues()).singleElement().satisfies(issue -> {
			assertThat(issue.line()).isEqualTo(21);
			assertThat(issue.message()).contains("答案");
		});
	}

	@Test
	void answerLetterMustBeAnOption() {
		ParseResult result = new QaBankParser()
			.parse(List.of("一、单项选择题", "1．题干", "A．甲 B．乙", "答案：E"));

		assertThat(result.items()).isEmpty();
		assertThat(result.issues()).singleElement().satisfies(issue -> assertThat(issue.message()).contains("E"));
	}

}
