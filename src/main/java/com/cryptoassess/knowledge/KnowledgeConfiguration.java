package com.cryptoassess.knowledge;

import java.time.Duration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
class KnowledgeConfiguration {

	@Bean
	EmbeddingClient embeddingClient(EmbeddingModel embeddingModel, EmbeddingProperties properties,
			@Value("${app.embedding.cache.enabled}") boolean cacheEnabled,
			@Value("${app.embedding.cache.ttl}") Duration cacheTtl, ObjectProvider<StringRedisTemplate> redis) {
		EmbeddingClient client = new SpringAiEmbeddingClient(embeddingModel, properties);
		return cacheEnabled ? new CachingEmbeddingClient(client, redis.getObject(), cacheTtl) : client;
	}

}
