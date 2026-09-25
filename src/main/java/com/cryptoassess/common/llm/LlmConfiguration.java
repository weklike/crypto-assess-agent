package com.cryptoassess.common.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class LlmConfiguration {

	@Bean
	PriceTable priceTable(@Value("${app.pricing.table:}") String table) {
		return PriceTable.parse(table);
	}

}
