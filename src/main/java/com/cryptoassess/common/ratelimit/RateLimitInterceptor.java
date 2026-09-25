package com.cryptoassess.common.ratelimit;

import java.util.Map;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 对会调用大模型的接口限流：带 API Key 的请求按客户端计，其余按来源地址计。超限返回 429 与 Retry-After。
 */
public class RateLimitInterceptor implements HandlerInterceptor {

	private final RateLimiter limiter;

	public RateLimitInterceptor(RateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		RateLimiter.Decision decision = this.limiter.tryAcquire(key(request));
		if (!decision.allowed()) {
			long seconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);
			response.setHeader("Retry-After", String.valueOf(seconds));
			throw new AppException(ErrorType.RATE_LIMITED, "too many requests, retry after " + seconds + " s",
					Map.of("retryAfterSeconds", seconds), null);
		}
		return true;
	}

	static String key(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().startsWith("SCOPE_"))) {
			return "client:" + auth.getName();
		}
		return "ip:" + request.getRemoteAddr();
	}

}
