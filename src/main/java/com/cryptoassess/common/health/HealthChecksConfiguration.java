package com.cryptoassess.common.health;

import com.cryptoassess.common.http.RestClients;
import com.cryptoassess.retrieval.RerankProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class HealthChecksConfiguration {

	@Bean
	OllamaCheck ollamaCheck(@Value("${spring.ai.ollama.base-url}") String baseUrl,
			@Value("${spring.ai.ollama.embedding.model}") String model, HealthProperties properties) {
		return new OllamaCheck(RestClients.create(baseUrl, properties.timeout()), model);
	}

	@Bean
	TeiCheck teiCheck(RerankProperties rerank, HealthProperties properties) {
		return new TeiCheck(RestClients.create(rerank.baseUrl(), properties.timeout()), rerank.model());
	}

	@Bean
	LlmConfigCheck llmConfigCheck(@Value("${spring.ai.openai.api-key:}") String apiKey,
			@Value("${spring.ai.openai.chat.model:}") String model) {
		return new LlmConfigCheck(apiKey, model);
	}

}
