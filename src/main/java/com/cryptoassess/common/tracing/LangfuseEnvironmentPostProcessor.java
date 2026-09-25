package com.cryptoassess.common.tracing;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 把 LANGFUSE_OTLP_ENDPOINT / LANGFUSE_PUBLIC_KEY / LANGFUSE_SECRET_KEY 转成 Spring Boot 的 OTLP 导出配置：
 * traces 路径补全为 …/v1/traces，Authorization 头为 Basic base64(公钥:私钥)。Langfuse 只支持 OTLP/HTTP。
 * 没配置端点时什么都不做，追踪也保持关闭（TRACING_ENABLED 默认 false）。
 * 放在配置加载之后执行，这样 .env 里的值也能读到。
 */
public class LangfuseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String SOURCE = "langfuseTracing";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String endpoint = environment.getProperty("app.tracing.langfuse.endpoint", "");
		if (endpoint.isBlank()) {
			return;
		}
		String publicKey = environment.getProperty("app.tracing.langfuse.public-key", "");
		String secretKey = environment.getProperty("app.tracing.langfuse.secret-key", "");
		if (publicKey.isBlank() || secretKey.isBlank()) {
			throw new IllegalStateException(
					"LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY are required when LANGFUSE_OTLP_ENDPOINT is set");
		}
		Map<String, Object> properties = new HashMap<>();
		properties.put("management.opentelemetry.tracing.export.otlp.endpoint", tracesEndpoint(endpoint));
		properties.put("management.opentelemetry.tracing.export.otlp.transport", "http");
		properties.put("management.opentelemetry.tracing.export.otlp.headers.Authorization",
				basicAuth(publicKey, secretKey));
		environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, properties));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	static String basicAuth(String publicKey, String secretKey) {
		return "Basic " + Base64.getEncoder()
			.encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
	}

	/** Langfuse 文档给的是 OTLP 根地址 …/api/public/otel，HTTP 导出器需要完整的 traces 路径。 */
	static String tracesEndpoint(String base) {
		String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return trimmed.endsWith("/v1/traces") ? trimmed : trimmed + "/v1/traces";
	}

}
