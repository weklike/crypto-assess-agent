package com.cryptoassess.common.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class LangfuseTracingTest {

	@Test
	void basicAuthHeaderIsBase64OfPublicAndSecretKey() {
		String header = LangfuseEnvironmentPostProcessor.basicAuth("pk-lf-1", "sk-lf-2");

		assertThat(header).startsWith("Basic ");
		assertThat(new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8))
			.isEqualTo("pk-lf-1:sk-lf-2");
	}

	@Test
	void mapsLangfuseSettingsToOtlpExporterProperties() {
		org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment()
			.withProperty("app.tracing.langfuse.endpoint", "https://cloud.langfuse.com/api/public/otel")
			.withProperty("app.tracing.langfuse.public-key", "pk")
			.withProperty("app.tracing.langfuse.secret-key", "sk");

		new LangfuseEnvironmentPostProcessor().postProcessEnvironment(env, null);

		assertThat(env.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
			.isEqualTo("https://cloud.langfuse.com/api/public/otel/v1/traces");
		assertThat(env.getProperty("management.opentelemetry.tracing.export.otlp.headers.Authorization"))
			.startsWith("Basic ");
	}

	@Test
	void nothingHappensWithoutEndpoint() {
		org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();

		new LangfuseEnvironmentPostProcessor().postProcessEnvironment(env, null);

		assertThat(env.getProperty("management.opentelemetry.tracing.export.otlp.endpoint")).isNull();
	}

	@Test
	void tracesEndpointIsDerivedFromLangfuseOtelBase() {
		assertThat(LangfuseEnvironmentPostProcessor.tracesEndpoint("https://cloud.langfuse.com/api/public/otel"))
			.isEqualTo("https://cloud.langfuse.com/api/public/otel/v1/traces");
		assertThat(LangfuseEnvironmentPostProcessor.tracesEndpoint("http://localhost:3000/api/public/otel/v1/traces"))
			.isEqualTo("http://localhost:3000/api/public/otel/v1/traces");
	}

}
