package com.cryptoassess.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从 X-API-Key 或 Authorization: Bearer 读取密钥。带了错误的密钥直接 401；没带密钥时交给授权规则决定。
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	private final ApiKeyRegistry registry;

	private final ProblemResponses problems;

	ApiKeyAuthenticationFilter(ApiKeyRegistry registry, ProblemResponses problems) {
		this.registry = registry;
		this.problems = problems;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER);
		String authorization = request.getHeader("Authorization");
		if ((key == null || key.isBlank()) && authorization != null && authorization.startsWith("Bearer ")) {
			key = authorization.substring("Bearer ".length());
		}
		if (key != null && !key.isBlank()) {
			var client = this.registry.authenticate(key);
			if (client.isEmpty()) {
				this.problems.unauthorized(response, "invalid API key");
				return;
			}
			List<SimpleGrantedAuthority> authorities = client.get()
				.scopes()
				.stream()
				.map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
				.toList();
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(client.get().clientId(), null,
						authorities));
		}
		chain.doFilter(request, response);
	}

}
