package com.cryptoassess.common.security;

import com.cryptoassess.common.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 两条过滤链：/mcp 必须带有效 API Key，工具按 scope 授权（MCP 的 HTTP 传输默认不鉴权）；
 * 其余接口中只有管理操作（导入标准、重建索引）需要 admin scope，检索、问答、评估接口保持开放（本机使用）。
 * 纯 JSON API、无会话无 Cookie，因此关闭 CSRF 并使用无状态会话。只在 Servlet Web 应用中生效（eval profile 不启动 Web）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	ApiKeyRegistry apiKeyRegistry(SecurityProperties properties) {
		ApiKeyRegistry registry = ApiKeyRegistry.parse(properties.apiKeys());
		if (registry.size() == 0) {
			log.warn("No API keys configured (MCP_API_KEYS); MCP and admin endpoints will reject all requests");
		}
		return registry;
	}

	/** 不使用用户名密码登录；提供空的用户表，免得 Boot 自动生成一个带随机密码的默认用户。 */
	@Bean
	InMemoryUserDetailsManager noUsers() {
		return new InMemoryUserDetailsManager();
	}

	@Bean
	@Order(1)
	SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, ApiKeyRegistry registry, AuditService auditService)
			throws Exception {
		ProblemResponses problems = new ProblemResponses();
		http.securityMatcher("/mcp", "/mcp/**")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new ApiKeyAuthenticationFilter(registry, problems), AnonymousAuthenticationFilter.class)
			.addFilterAfter(new McpScopeFilter(problems, auditService), AuthorizationFilter.class)
			.authorizeHttpRequests(a -> a.anyRequest().authenticated())
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> problems.unauthorized(res, "API key required"))
				.accessDeniedHandler((req, res, ex) -> problems.forbidden(res, "access denied", java.util.Map.of())));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiKeyRegistry registry) throws Exception {
		ProblemResponses problems = new ProblemResponses();
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new ApiKeyAuthenticationFilter(registry, problems), AnonymousAuthenticationFilter.class)
			.authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST, "/api/kb/documents", "/api/kb/reindex")
				.hasAuthority("SCOPE_admin")
				.anyRequest()
				.permitAll())
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> problems.unauthorized(res, "API key with admin scope required"))
				.accessDeniedHandler(
						(req, res, ex) -> problems.forbidden(res, "admin scope required", java.util.Map.of())));
		return http.build();
	}

}
