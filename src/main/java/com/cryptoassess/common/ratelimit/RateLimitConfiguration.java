package com.cryptoassess.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RateLimitConfiguration {

	@Bean
	RateLimiter rateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
		return new RedisTokenBucketRateLimiter(redis, properties);
	}

	/** 只限制会触发模型调用的接口。 */
	@Bean
	WebMvcConfigurer rateLimitWebMvcConfigurer(RateLimiter limiter) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(new RateLimitInterceptor(limiter))
					.addPathPatterns("/api/qa", "/api/assessments/*/analyze");
			}
		};
	}

}
