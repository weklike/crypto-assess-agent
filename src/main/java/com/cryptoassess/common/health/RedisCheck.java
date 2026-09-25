package com.cryptoassess.common.health;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
class RedisCheck implements DependencyCheck {

	private final RedisConnectionFactory connectionFactory;

	RedisCheck(RedisConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	@Override
	public String name() {
		return "redis";
	}

	@Override
	public CheckResult check() {
		try (RedisConnection connection = this.connectionFactory.getConnection()) {
			String pong = connection.ping();
			return "PONG".equalsIgnoreCase(pong) ? CheckResult.up(pong) : CheckResult.down("unexpected reply " + pong);
		}
	}

}
