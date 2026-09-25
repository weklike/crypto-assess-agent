package com.cryptoassess.eval.qabank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

class OllamaExamChatModelTest {

	private static OllamaExamChatModel model(String baseUrl, String name) {
		return new OllamaExamChatModel(OllamaApi.builder().baseUrl(baseUrl).build(), () -> baseUrl,
				new QaExamProperties(name, List.of("localhost", "127.0.0.1", "::1", "ollama")), ObservationRegistry.NOOP);
	}

	@Test
	void remoteEndpointIsRefusedBeforeAnyModelIsCreated() {
		assertThatThrownBy(() -> model("https://ollama.example.com", "qwen2.5:3b").chatModel())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ollama.example.com");
	}

	@Test
	void localEndpointBuildsOllamaModelWithTemperatureZeroAndJsonFormat() {
		OllamaExamChatModel exam = model("http://localhost:11434", "qwen2.5:3b");

		assertThat(exam.chatModel()).isInstanceOf(OllamaChatModel.class);
		OllamaChatOptions options = (OllamaChatOptions) exam.chatModel().getOptions();
		assertThat(options.getModel()).isEqualTo("qwen2.5:3b");
		assertThat(options.getTemperature()).isEqualTo(0.0);
		assertThat(options.getFormat()).isEqualTo("json");
		assertThat(exam.endpoint()).isEqualTo("ollama@http://localhost:11434");
	}

}
