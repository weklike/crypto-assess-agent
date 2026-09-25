package com.cryptoassess.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.cryptoassess.support.FakeEmbeddingModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EmbeddingCacheIT {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

	static StringRedisTemplate redis;

	@BeforeAll
	static void connect() {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
		factory.afterPropertiesSet();
		redis = new StringRedisTemplate(factory);
		redis.afterPropertiesSet();
	}

	@Test
	void secondLookupIsServedFromRedisAndOnlyMissesAreEmbedded() {
		FakeEmbeddingModel model = new FakeEmbeddingModel();
		EmbeddingProperties props = new EmbeddingProperties("bge-m3", 1024, 16, Duration.ofSeconds(5));
		CachingEmbeddingClient client = new CachingEmbeddingClient(new SpringAiEmbeddingClient(model, props), redis,
				Duration.ofMinutes(5));

		List<float[]> first = client.embed(List.of("身份鉴别", "密钥管理"));
		List<float[]> second = client.embed(List.of("身份鉴别", "密钥管理"));
		client.embed(List.of("身份鉴别", "应急预案"));

		assertThat(model.calls()).isEqualTo(2);
		assertThat(second.get(1)).containsExactly(first.get(1));
		assertThat(second.get(0)).containsExactly(FakeEmbeddingModel.vector("身份鉴别", 1024));
		EmbeddingClient.CacheStats stats = client.cacheStats().orElseThrow();
		assertThat(stats.hits()).isEqualTo(3);
		assertThat(stats.misses()).isEqualTo(3);
		assertThat(redis.keys("emb:bge-m3:*")).hasSize(3);
		assertThat(redis.getExpire(redis.keys("emb:bge-m3:*").iterator().next())).isPositive();
	}

}
