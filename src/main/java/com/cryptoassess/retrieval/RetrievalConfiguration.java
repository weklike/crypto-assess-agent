package com.cryptoassess.retrieval;

import com.cryptoassess.common.http.RestClients;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RetrievalConfiguration {

	@Bean
	@ConditionalOnMissingBean(RerankClient.class)
	RerankClient rerankClient(RerankProperties properties) {
		return new TeiRerankClient(RestClients.create(properties.baseUrl(), properties.timeout()));
	}

}
