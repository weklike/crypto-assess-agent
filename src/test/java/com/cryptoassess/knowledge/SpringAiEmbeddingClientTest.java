package com.cryptoassess.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.support.FakeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.client.ResourceAccessException;

class SpringAiEmbeddingClientTest {

	private static final EmbeddingProperties PROPS = new EmbeddingProperties("bge-m3", 1024, 4, Duration.ofSeconds(2));

	@Test
	void splitsIntoBatchesAndKeepsOrder() {
		FakeEmbeddingModel model = new FakeEmbeddingModel();
		SpringAiEmbeddingClient client = new SpringAiEmbeddingClient(model, PROPS);
		List<String> texts = IntStream.range(0, 10).mapToObj(i -> "条款" + i).toList();

		List<float[]> vectors = client.embed(texts);

		assertThat(model.calls()).isEqualTo(3);
		assertThat(vectors).hasSize(10);
		assertThat(vectors.get(7)).containsExactly(FakeEmbeddingModel.vector("条款7", 1024));
	}

	@Test
	void wrongDimensionFailsFast() {
		SpringAiEmbeddingClient client = new SpringAiEmbeddingClient(new FakeEmbeddingModel(768), PROPS);

		assertThatThrownBy(() -> client.embed(List.of("文本"))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("768")
			.hasMessageContaining("1024");
	}

	@Test
	void unreachableModelIsDependencyUnavailable() {
		SpringAiEmbeddingClient client = new SpringAiEmbeddingClient(new FakeEmbeddingModel() {
			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				throw new ResourceAccessException("Connection refused");
			}
		}, PROPS);

		assertThatThrownBy(() -> client.embed(List.of("文本"))).isInstanceOf(AppException.class)
			.extracting(ex -> ((AppException) ex).type())
			.isEqualTo(ErrorType.DEPENDENCY_UNAVAILABLE);
	}

	@Test
	void slowModelTimesOut() {
		SpringAiEmbeddingClient client = new SpringAiEmbeddingClient(new FakeEmbeddingModel() {
			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				try {
					Thread.sleep(10_000);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return super.call(request);
			}
		}, new EmbeddingProperties("bge-m3", 1024, 4, Duration.ofMillis(200)));

		long start = System.nanoTime();
		assertThatThrownBy(() -> client.embed(List.of("文本"))).isInstanceOf(AppException.class)
			.hasMessageContaining("timed out");
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
	}

	@Test
	void emptyInputMakesNoCall() {
		FakeEmbeddingModel model = new FakeEmbeddingModel();

		assertThat(new SpringAiEmbeddingClient(model, PROPS).embed(List.of())).isEmpty();
		assertThat(model.calls()).isZero();
	}

}
