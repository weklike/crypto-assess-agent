package com.cryptoassess.eval.qabank;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalModelGuardTest {

	private static final List<String> ALLOWED = List.of("localhost", "127.0.0.1", "::1", "ollama");

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:11434", "http://127.0.0.1:11434/", "http://[::1]:11434",
			"http://ollama:11434", "http://LOCALHOST:11434" })
	void localEndpointsAreAllowed(String baseUrl) {
		assertThatCode(() -> LocalModelGuard.check(baseUrl, "qwen2.5:3b", ALLOWED)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://api.deepseek.com", "http://10.0.0.5:11434", "http://localhost.evil.com:11434",
			"ftp://localhost:11434", "not a url", "" })
	void remoteOrMalformedEndpointsAreRefused(String baseUrl) {
		assertThatThrownBy(() -> LocalModelGuard.check(baseUrl, "qwen2.5:3b", ALLOWED))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("local");
	}

	@ParameterizedTest
	@ValueSource(strings = { "gpt-oss:120b-cloud", "deepseek-v3.1:671b-cloud", "kimi:cloud" })
	void ollamaCloudModelsAreRefusedEvenOnLocalhost(String model) {
		// Ollama 的 *-cloud 模型经本地服务转发到 ollama.com，题目会离开本机
		assertThatThrownBy(() -> LocalModelGuard.check("http://localhost:11434", model, ALLOWED))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cloud");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " " })
	void modelNameIsRequired(String model) {
		assertThatThrownBy(() -> LocalModelGuard.check("http://localhost:11434", model, ALLOWED))
			.isInstanceOf(IllegalStateException.class);
	}

}
