package com.cryptoassess.common.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PriceTableTest {

	@Test
	void computesCostInYuanFromPricePerMillionTokens() {
		PriceTable table = PriceTable.parse("deepseek-chat:2:8; gpt-5-mini:1.8:14.4");

		// 1500 × 2 / 1e6 + 500 × 8 / 1e6 = 0.003 + 0.004 = 0.007
		assertThat(table.cost("deepseek-chat", 1500, 500)).isEqualByComparingTo("0.007000");
		assertThat(table.cost("gpt-5-mini", 1_000_000, 0)).isEqualByComparingTo("1.800000");
		assertThat(table.cost("deepseek-chat", 1500, 500).scale()).isEqualTo(6);
	}

	@Test
	void unknownModelOrMissingTokensGiveNoCost() {
		PriceTable table = PriceTable.parse("deepseek-chat:2:8");

		assertThat(table.cost("other", 10, 10)).isNull();
		assertThat(table.cost("deepseek-chat", null, 10)).isNull();
		assertThat(PriceTable.parse("").cost("deepseek-chat", 1, 1)).isNull();
	}

	@Test
	void invalidConfigurationFailsFast() {
		assertThatThrownBy(() -> PriceTable.parse("model:abc:1")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("model");
		assertThatThrownBy(() -> PriceTable.parse("model:1")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PriceTable.parse("model:-1:1")).isInstanceOf(IllegalArgumentException.class);
	}

}
