package com.cryptoassess.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisTokenBucketIT {

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

	private RedisTokenBucketRateLimiter limiter(int capacity, double refillPerSecond) {
		return new RedisTokenBucketRateLimiter(redis, new RateLimitProperties(true, capacity, refillPerSecond));
	}

	@Test
	void concurrentRequestsBeyondCapacityAreRejected() throws Exception {
		RedisTokenBucketRateLimiter limiter = limiter(5, 0.01);
		List<Callable<RateLimiter.Decision>> tasks = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			tasks.add(() -> limiter.tryAcquire("client-a"));
		}
		int allowed = 0;
		try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
			for (Future<RateLimiter.Decision> f : pool.invokeAll(tasks)) {
				allowed += f.get().allowed() ? 1 : 0;
			}
		}

		assertThat(allowed).isEqualTo(5);
		RateLimiter.Decision rejected = limiter.tryAcquire("client-a");
		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.retryAfterMs()).isPositive();
	}

	@Test
	void tokensRefillOverTime() throws Exception {
		RedisTokenBucketRateLimiter limiter = limiter(2, 5.0);
		assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
		assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
		assertThat(limiter.tryAcquire("client-b").allowed()).isFalse();

		Thread.sleep(450);

		assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
	}

	@Test
	void bucketsAreIndependentPerKey() {
		RedisTokenBucketRateLimiter limiter = limiter(1, 0.01);

		assertThat(limiter.tryAcquire("client-c").allowed()).isTrue();
		assertThat(limiter.tryAcquire("client-c").allowed()).isFalse();
		assertThat(limiter.tryAcquire("client-d").allowed()).isTrue();
	}

	@Test
	void bucketKeyExpires() {
		limiter(3, 1.0).tryAcquire("client-e");

		Long ttl = redis.getExpire("ratelimit:client-e");
		assertThat(ttl).isPositive().isLessThanOrEqualTo(10);
	}

}
