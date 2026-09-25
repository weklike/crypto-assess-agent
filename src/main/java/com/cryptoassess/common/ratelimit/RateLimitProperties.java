package com.cryptoassess.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled 是否启用
 * @param capacity 桶容量（突发上限）
 * @param refillPerSecond 每秒补充的令牌数
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, int capacity, double refillPerSecond) {

}
