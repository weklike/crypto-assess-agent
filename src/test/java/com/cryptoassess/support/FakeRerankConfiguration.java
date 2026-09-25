package com.cryptoassess.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeRerankConfiguration {

	@Bean
	@Primary
	FakeRerankClient fakeRerankClient() {
		return new FakeRerankClient();
	}

}
