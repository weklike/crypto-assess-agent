package com.cryptoassess.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.client.RestClientException;

/**
 * 基于 Spring AI {@link EmbeddingModel}（默认 Ollama bge-m3）的实现。
 * 每批单独限时；维度不符直接失败，避免把错误维度的向量写进索引。
 */
public class SpringAiEmbeddingClient implements EmbeddingClient {

	private final EmbeddingModel embeddingModel;

	private final EmbeddingProperties properties;

	public SpringAiEmbeddingClient(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
		this.embeddingModel = embeddingModel;
		this.properties = properties;
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		List<float[]> vectors = new ArrayList<>(texts.size());
		for (int from = 0; from < texts.size(); from += this.properties.batchSize()) {
			List<String> batch = texts.subList(from, Math.min(from + this.properties.batchSize(), texts.size()));
			List<float[]> result = embedBatch(batch);
			if (result.size() != batch.size()) {
				throw new IllegalStateException(
						"embedding model returned " + result.size() + " vectors for " + batch.size() + " texts");
			}
			for (float[] vector : result) {
				if (vector.length != this.properties.dimensions()) {
					throw new IllegalStateException("embedding dimension " + vector.length + " does not match expected "
							+ this.properties.dimensions());
				}
			}
			vectors.addAll(result);
		}
		return vectors;
	}

	@Override
	public String model() {
		return this.properties.model();
	}

	private List<float[]> embedBatch(List<String> batch) {
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<List<float[]>> future = executor.submit(() -> this.embeddingModel.embed(batch));
			try {
				return future.get(this.properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
			}
			catch (TimeoutException ex) {
				future.cancel(true);
				throw new AppException(ErrorType.DEPENDENCY_UNAVAILABLE,
						"embedding timed out after " + this.properties.timeout().toMillis() + " ms", ex);
			}
			catch (ExecutionException ex) {
				if (ex.getCause() instanceof RestClientException cause) {
					throw AppException.unavailable("embedding model " + this.properties.model(), cause);
				}
				if (ex.getCause() instanceof RuntimeException cause) {
					throw cause;
				}
				throw new IllegalStateException(ex.getCause());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted while embedding", ex);
			}
		}
	}

}
