package com.cryptoassess.common.ratelimit;

import java.util.List;

import com.cryptoassess.common.error.AppException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis Lua 脚本（redis/token_bucket.lua）的令牌桶。Redis 不可用时抛 503，不放行也不静默跳过限流。
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

	private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();

	static {
		SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/token_bucket.lua")));
		SCRIPT.setResultType(List.class);
	}

	private final StringRedisTemplate redis;

	private final RateLimitProperties properties;

	public RedisTokenBucketRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	@Override
	public Decision tryAcquire(String key) {
		List<?> result;
		try {
			result = this.redis.execute(SCRIPT, List.of("ratelimit:" + key),
					String.valueOf(this.properties.capacity()), String.valueOf(this.properties.refillPerSecond()), "1");
		}
		catch (DataAccessException ex) {
			throw AppException.unavailable("redis", ex);
		}
		if (result == null || result.size() != 3) {
			throw AppException.unavailable("redis", new IllegalStateException("unexpected token bucket result"));
		}
		return new Decision(((Number) result.get(0)).longValue() == 1, Double.parseDouble(String.valueOf(result.get(1))),
				((Number) result.get(2)).longValue());
	}

}
