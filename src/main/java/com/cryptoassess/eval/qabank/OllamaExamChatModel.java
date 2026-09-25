package com.cryptoassess.eval.qabank;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * 本地 Ollama 对话模型，只在题库评测运行时才创建，创建前先过 {@link LocalModelGuard}。
 * Spring AI 的 OllamaChatModel 默认带重试模板，这里换成不重试；也不自动拉取模型。
 */
@Component
class OllamaExamChatModel implements ExamChatModel {

	private final OllamaApi ollamaApi;

	private final String baseUrl;

	private final QaExamProperties properties;

	private final ObservationRegistry observationRegistry;

	private volatile ChatModel chatModel;

	OllamaExamChatModel(OllamaApi ollamaApi, OllamaConnectionDetails connectionDetails, QaExamProperties properties,
			ObservationRegistry observationRegistry) {
		this.ollamaApi = ollamaApi;
		this.baseUrl = connectionDetails.getBaseUrl();
		this.properties = properties;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public synchronized ChatModel chatModel() {
		if (this.chatModel == null) {
			LocalModelGuard.check(this.baseUrl, this.properties.model(), this.properties.allowedHosts());
			this.chatModel = OllamaChatModel.builder()
				.ollamaApi(this.ollamaApi)
				.options(OllamaChatOptions.builder().model(this.properties.model()).temperature(0.0).format("json").build())
				.modelManagementOptions(ModelManagementOptions.defaults())
				.retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()))
				.observationRegistry(this.observationRegistry)
				.build();
		}
		return this.chatModel;
	}

	@Override
	public String modelName() {
		return this.properties.model();
	}

	@Override
	public String endpoint() {
		return "ollama@" + this.baseUrl;
	}

}
