package com.cryptoassess.common.ratelimit;

/**
 * 限流器。按调用方标识（API Key 客户端或来源地址）各自一个桶。
 */
@FunctionalInterface
public interface RateLimiter {

	/**
	 * @param retryAfterMs 被拒绝时，距离下一个令牌可用的毫秒数
	 */
	record Decision(boolean allowed, double remaining, long retryAfterMs) {
	}

	Decision tryAcquire(String key);

}
