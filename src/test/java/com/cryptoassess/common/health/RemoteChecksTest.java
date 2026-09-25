package com.cryptoassess.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteChecksTest {

	@Test
	void ollamaUpWhenEmbeddingModelIsPulled() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://ollama/api/tags"))
			.andRespond(withSuccess("{\"models\":[{\"name\":\"bge-m3:latest\"},{\"name\":\"qwen3:8b\"}]}",
					MediaType.APPLICATION_JSON));

		CheckResult result = new OllamaCheck(builder.build(), "bge-m3").check();

		assertThat(result.status()).isEqualTo(CheckResult.Status.UP);
	}

	@Test
	void ollamaDownWhenEmbeddingModelIsMissing() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://ollama/api/tags"))
			.andRespond(withSuccess("{\"models\":[{\"name\":\"qwen3:8b\"}]}", MediaType.APPLICATION_JSON));

		CheckResult result = new OllamaCheck(builder.build(), "bge-m3").check();

		assertThat(result.status()).isEqualTo(CheckResult.Status.DOWN);
		assertThat(result.detail()).contains("bge-m3").contains("not pulled");
	}

	@Test
	void ollamaModelNameMatchingIgnoresLatestTagOnly() {
		assertThat(OllamaCheck.isPulled(List.of("bge-m3:latest"), "bge-m3")).isTrue();
		assertThat(OllamaCheck.isPulled(List.of("bge-m3:567m"), "bge-m3:567m")).isTrue();
		assertThat(OllamaCheck.isPulled(List.of("bge-m3-large:latest"), "bge-m3")).isFalse();
	}

	@Test
	void teiUpWhenHealthReturns200AndModelMatches() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://tei/health")).andRespond(withSuccess());
		server.expect(requestTo("http://tei/info"))
			.andRespond(withSuccess("{\"model_id\":\"BAAI/bge-reranker-v2-m3\",\"model_type\":{\"reranker\":{}}}",
					MediaType.APPLICATION_JSON));

		CheckResult result = new TeiCheck(builder.build(), "BAAI/bge-reranker-v2-m3").check();

		assertThat(result.status()).isEqualTo(CheckResult.Status.UP);
	}

	@Test
	void teiDownWhenServingAnotherModel() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://tei/health")).andRespond(withSuccess());
		server.expect(requestTo("http://tei/info"))
			.andRespond(withSuccess("{\"model_id\":\"BAAI/bge-m3\"}", MediaType.APPLICATION_JSON));

		CheckResult result = new TeiCheck(builder.build(), "BAAI/bge-reranker-v2-m3").check();

		assertThat(result.status()).isEqualTo(CheckResult.Status.DOWN);
		assertThat(result.detail()).contains("BAAI/bge-m3");
	}

	@Test
	void teiModelMatchingAcceptsLocalSnapshotOfSameModel() {
		assertThat(TeiCheck.servesModel("BAAI/bge-reranker-v2-m3", "BAAI/bge-reranker-v2-m3")).isTrue();
		assertThat(TeiCheck.servesModel(
				"/data/models--BAAI--bge-reranker-v2-m3/snapshots/953dc6f6f85a1b2dbfca4c34a2796e7dde08d41e",
				"BAAI/bge-reranker-v2-m3")).isTrue();
		assertThat(TeiCheck.servesModel("/data/models--BAAI--bge-m3/snapshots/abc", "BAAI/bge-reranker-v2-m3"))
			.isFalse();
		assertThat(TeiCheck.servesModel(null, "BAAI/bge-reranker-v2-m3")).isFalse();
	}

	@Test
	void teiDownWhenHealthFails() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://tei/health")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		CheckResult result = new TeiCheck(builder.build(), "BAAI/bge-reranker-v2-m3").check();

		assertThat(result.status()).isEqualTo(CheckResult.Status.DOWN);
	}

	@Test
	void ollamaDownOnServerError() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://ollama/api/tags")).andRespond(withServerError());

		assertThat(new OllamaCheck(builder.build(), "bge-m3").check().status()).isEqualTo(CheckResult.Status.DOWN);
	}

	@Test
	void llmConfigDownWhenApiKeyOrModelMissing() {
		assertThat(new LlmConfigCheck("", "gpt-5-mini").check().status()).isEqualTo(CheckResult.Status.DOWN);
		assertThat(new LlmConfigCheck("sk-x", " ").check().status()).isEqualTo(CheckResult.Status.DOWN);
		CheckResult up = new LlmConfigCheck("sk-secret-value", "deepseek-chat").check();
		assertThat(up.status()).isEqualTo(CheckResult.Status.UP);
		assertThat(up.detail()).contains("deepseek-chat").doesNotContain("sk-secret-value");
	}

	@Test
	void ikSegmentationDetection() {
		assertThat(ElasticsearchCheck.isWordSegmented(List.of("商用", "密码"))).isTrue();
		assertThat(ElasticsearchCheck.isWordSegmented(List.of("商", "用", "密", "码"))).isFalse();
		assertThat(ElasticsearchCheck.isWordSegmented(List.of())).isFalse();
	}

}
