package com.cryptoassess.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CitationParserTest {

	static Stream<Arguments> cases() {
		return Stream.of(
				Arguments.of("半角方括号", "应加密[GB/T 39786-2021#6.2.1]。", List.of("GB/T 39786-2021#6.2.1")),
				Arguments.of("全角方头括号", "应加密【GB/T 39786-2021#6.2.1】。", List.of("GB/T 39786-2021#6.2.1")),
				Arguments.of("全角方括号与井号两侧空格", "应加密［GB/T 39786-2021 # 6.2.1］", List.of("GB/T 39786-2021#6.2.1")),
				Arguments.of("多余空格与长破折号", "见[ GB/T  39786—2021#6.2.1 ]", List.of("GB/T 39786-2021#6.2.1")),
				Arguments.of("标准号内缺空格", "见[GB/T39786-2021#6.2.1]", List.of("GB/T 39786-2021#6.2.1")),
				Arguments.of("连续多个引用", "见[FIX/T 0001-2026#5.2.3][FIX/T 0001-2026#5.4.3]",
						List.of("FIX/T 0001-2026#5.2.3", "FIX/T 0001-2026#5.4.3")),
				Arguments.of("同一括号内分号分隔", "见[FIX/T 0001-2026#5.2.3；FIX/T 0001-2026#5.4.3]",
						List.of("FIX/T 0001-2026#5.2.3", "FIX/T 0001-2026#5.4.3")),
				Arguments.of("重复引用去重并保持顺序", "[A/T 1-2020#2] 然后 [A/T 1-2020#1] 再 [A/T 1-2020#2]",
						List.of("A/T 1-2020#2", "A/T 1-2020#1")),
				Arguments.of("附录编号", "见[FIX/T 0001-2026#A.1]", List.of("FIX/T 0001-2026#A.1")),
				Arguments.of("不带斜杠的国标号", "见[GB 12345-2020#3]", List.of("GB 12345-2020#3")),
				Arguments.of("非引用的方括号忽略", "[注意] 参见文献[1]，没有引用", List.of()));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void parsesAndNormalizesCitations(String name, String text, List<String> expected) {
		assertThat(CitationParser.parse(text)).containsExactlyElementsOf(expected);
	}

}
