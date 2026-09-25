package com.cryptoassess.common.llm;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 结构化输出调用：Spring AI 的 StructuredOutputValidationAdvisor（重试次数设为 0，只做一次）
 * 之外再用同一份 JSON Schema 显式校验一遍——advisor 校验失败时只打警告并照常返回，
 * 必须由这里把不合规输出变成明确的错误。不做隐藏重试，也不做兜底解析。
 */
@Component
public class StructuredLlmClient {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ChatClient chatClient;

	private final LlmCallRecorder recorder;

	private final String configuredModel;

	private final ChatModel chatModel;

	private final LlmRequestOptions requestOptions;

	public StructuredLlmClient(ChatModel chatModel, LlmCallRecorder recorder,
			@Value("${spring.ai.openai.chat.model:unknown}") String configuredModel, LlmRequestOptions requestOptions) {
		this.chatClient = ChatClient.create(chatModel);
		this.chatModel = chatModel;
		this.recorder = recorder;
		this.configuredModel = configuredModel;
		this.requestOptions = requestOptions;
	}

	/** 同样的校验与调用记录，换一个模型（如题库评测只能用的本地模型）。 */
	public StructuredLlmClient withModel(ChatModel model, String modelName) {
		return new StructuredLlmClient(model, this.recorder, modelName, this.requestOptions);
	}

	public <T> StructuredResult<T> call(StructuredRequest request, Class<T> type) {
		long start = System.nanoTime();
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputJsonSchema(JsonSchemas.text(request.schema()))
			.maxRepeatAttempts(0)
			.build();
		ChatOptions.Builder<?> options = this.requestOptions.builder(this.chatModel, request.temperature());
		ChatResponse response;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<ChatResponse> future = executor.submit(() -> this.chatClient.prompt()
				.options(options)
				.user(request.prompt())
				.advisors(advisor)
				.call()
				.chatResponse());
			try {
				response = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
			}
			catch (TimeoutException ex) {
				future.cancel(true);
				record(request, this.configuredModel, LlmCallStatus.TIMEOUT, "timeout", start, null);
				throw new AppException(ErrorType.LLM_TIMEOUT,
						"model call exceeded " + request.timeout().toMillis() + " ms", ex);
			}
			catch (ExecutionException ex) {
				if (LlmErrors.isTimeout(ex.getCause())) {
					record(request, this.configuredModel, LlmCallStatus.TIMEOUT, "timeout", start, null);
					throw new AppException(ErrorType.LLM_TIMEOUT, "model call timed out in the HTTP client",
							ex.getCause());
				}
				record(request, this.configuredModel, LlmCallStatus.ERROR, ex.getCause().getClass().getSimpleName(),
						start, null);
				throw new AppException(ErrorType.DEPENDENCY_UNAVAILABLE, "model call failed", ex.getCause());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted during model call", ex);
			}
		}
		String model = (response != null && response.getMetadata() != null && response.getMetadata().getModel() != null
				&& !response.getMetadata().getModel().isBlank()) ? response.getMetadata().getModel()
						: this.configuredModel;
		Usage usage = (response != null && response.getMetadata() != null) ? response.getMetadata().getUsage() : null;
		String text = (response == null || response.getResult() == null) ? null
				: response.getResult().getOutput().getText();
		JsonNode node;
		try {
			node = JSON.readTree(stripFence(text));
		}
		catch (JacksonException | IllegalArgumentException ex) {
			record(request, model, LlmCallStatus.INVALID_OUTPUT, "invalid_json", start, usage);
			throw new AppException(ErrorType.LLM_INVALID_OUTPUT, "model output is not valid JSON", ex);
		}
		List<String> errors = JsonSchemas.validate(request.schema(), node);
		if (!errors.isEmpty()) {
			record(request, model, LlmCallStatus.INVALID_OUTPUT, "schema_violation", start, usage);
			throw new AppException(ErrorType.LLM_INVALID_OUTPUT,
					"model output violates schema " + request.schema() + ": " + errors,
					Map.of("schemaErrors", errors), null);
		}
		T value = JSON.treeToValue(node, type);
		long id = record(request, model, LlmCallStatus.OK, null, start, usage);
		return new StructuredResult<>(value, id, model, (usage == null) ? null : usage.getPromptTokens(),
				(usage == null) ? null : usage.getCompletionTokens(), millisSince(start));
	}

	static String stripFence(String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("empty output");
		}
		String trimmed = text.strip();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			int lastFence = trimmed.lastIndexOf("```");
			if (firstNewline > 0 && lastFence > firstNewline) {
				return trimmed.substring(firstNewline + 1, lastFence).strip();
			}
		}
		return trimmed;
	}

	private long record(StructuredRequest request, String model, LlmCallStatus status, String errorCode, long start,
			Usage usage) {
		return this.recorder.record(new LlmCall(request.purpose(), model, request.promptVersion(),
				(int) millisSince(start), (usage == null) ? null : usage.getPromptTokens(),
				(usage == null) ? null : usage.getCompletionTokens(), null, status, errorCode, Instant.now()));
	}

	private static long millisSince(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

}
