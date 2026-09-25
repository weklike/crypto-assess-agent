package com.cryptoassess.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestcontainers {

	@Bean
	@ServiceConnection
	ElasticsearchContainer elasticsearchContainer() {
		return ElasticsearchIkImage.newContainer();
	}

}
