package com.cryptoassess.common.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.support.FakeChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StructuredLlmClientTest {

	record ExamAnswer(List<String> answer, List<String> citations) {
	}

	private final FakeChatModel chatModel = new FakeChatModel();

	private final LlmCallRecorder recorder = mock(LlmCallRecorder.class);

	private StructuredLlmClient client;

	@BeforeEach
	void setUp() {
		given(recorder.record(any())).willReturn(42L);
		client = new StructuredLlmClient(chatModel, recorder, "configured-model",
				new LlmRequestOptions(Duration.ofSeconds(180)));
	}

	private StructuredRequest request(Duration timeout) {
		return new StructuredRequest("exam", "qa-exam.v1", "请作答", "qa-exam-answer.v1", 0.0, timeout);
	}

	@Test
	void parsesValidJsonAndRecordsOkCall() {
		chatModel.enqueue(FakeChatModel.reply("{\"answer\":[\"B\",\"A\"],\"citations\":[\"F#1\"]}"));

		StructuredResult<ExamAnswer> result = client.call(request(Duration.ofSeconds(5)), ExamAnswer.class);

		assertThat(result.value().answer()).containsExactly("B", "A");
		assertThat(result.llmCallId()).isEqualTo(42L);
		assertThat(recordedStatus()).isEqualTo(LlmCallStatus.OK);
	}

	@Test
	void requestCarriesModelOptionsWithExplicitTimeout() {
		chatModel.enqueue(FakeChatModel.reply("{\"answer\":[\"A\"],\"citations\":[]}"));

		client.call(request(Duration.ofSeconds(5)), ExamAnswer.class);

		var options = (org.springframework.ai.openai.OpenAiChatOptions) chatModel.prompts().getFirst().getOptions();
		assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(180));
		assertThat(options.getTemperature()).isEqualTo(0.0);
	}

	@Test
	void stripsMarkdownCodeFence() {
		chatModel.enqueue(FakeChatModel.reply("```json\n{\"answer\":[\"C\"],\"citations\":[]}\n```"));

		assertThat(client.call(request(Duration.ofSeconds(5)), ExamAnswer.class).value().answer()).containsExactly("C");
	}

	@Test
	void invalidJsonIsAnErrorNotAFallback() {
		chatModel.enqueue(FakeChatModel.reply("答案是 A"));

		assertThatThrownBy(() -> client.call(request(Duration.ofSeconds(5)), ExamAnswer.class))
			.isInstanceOf(AppException.class)
			.extracting(ex -> ((AppException) ex).type())
			.isEqualTo(ErrorType.LLM_INVALID_OUTPUT);
		assertThat(recordedStatus()).isEqualTo(LlmCallStatus.INVALID_OUTPUT);
		assertThat(chatModel.calls()).isEqualTo(1);
	}

	@Test
	void schemaViolationIsAnError() {
		chatModel.enqueue(FakeChatModel.reply("{\"answer\":[\"Z\"],\"citations\":[],\"extra\":1}"));

		assertThatThrownBy(() -> client.call(request(Duration.ofSeconds(5)), ExamAnswer.class))
			.isInstanceOf(AppException.class)
			.hasMessageContaining("schema");
		assertThat(recordedStatus()).isEqualTo(LlmCallStatus.INVALID_OUTPUT);
		// 不做隐藏重试
		assertThat(chatModel.calls()).isEqualTo(1);
	}

	@Test
	void timeoutIsRecorded() {
		chatModel.enqueue(FakeChatModel.hang(Duration.ofSeconds(10)));

		assertThatThrownBy(() -> client.call(request(Duration.ofMillis(300)), ExamAnswer.class))
			.isInstanceOf(AppException.class)
			.extracting(ex -> ((AppException) ex).type())
			.isEqualTo(ErrorType.LLM_TIMEOUT);
		assertThat(recordedStatus()).isEqualTo(LlmCallStatus.TIMEOUT);
	}

	@Test
	void transportTimeoutInsideSdkIsATimeoutNotADependencyFailure() {
		RuntimeException sdkError = new RuntimeException("request failed",
				new java.io.InterruptedIOException("timeout"));
		chatModel.enqueue(FakeChatModel.fail(sdkError));

		assertThatThrownBy(() -> client.call(request(Duration.ofSeconds(5)), ExamAnswer.class))
			.isInstanceOf(AppException.class)
			.extracting(ex -> ((AppException) ex).type())
			.isEqualTo(ErrorType.LLM_TIMEOUT);
		assertThat(recordedStatus()).isEqualTo(LlmCallStatus.TIMEOUT);
	}

	@Test
	void otherSdkFailureIsDependencyUnavailable() {
		chatModel.enqueue(FakeChatModel.fail(new IllegalStateException("401 unauthorized")));

		assertThatThrownBy(() -> client.call(request(Duration.ofSeconds(5)), ExamAnswer.class))
			.isInstanceOf(AppException.class)
			.extracting(ex -> ((AppException) ex).type())
			.isEqualTo(ErrorType.DEPENDENCY_UNAVAILABLE);
		assertThat(recordedStatus()).isEqualTo(LlmCallStatus.ERROR);
	}

	@Test
	void timeoutDetectionWalksTheCauseChain() {
		assertThat(LlmErrors.isTimeout(new RuntimeException(new java.net.SocketTimeoutException()))).isTrue();
		assertThat(LlmErrors.isTimeout(new RuntimeException(new java.net.http.HttpTimeoutException("x")))).isTrue();
		assertThat(LlmErrors.isTimeout(new java.util.concurrent.TimeoutException())).isTrue();
		assertThat(LlmErrors.isTimeout(new RuntimeException(new java.io.IOException("reset")))).isFalse();
	}

	private LlmCallStatus recordedStatus() {
		ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
		verify(recorder).record(captor.capture());
		return captor.getValue().status();
	}

}
