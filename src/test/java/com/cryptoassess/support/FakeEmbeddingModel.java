package com.cryptoassess.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 确定性的假向量模型：把文本的单字和相邻二字组哈希到 1024 维上再归一化。
 * 字面重合越多余弦越大，足够让向量检索的测试有意义，又不依赖真实模型。
 */
public class FakeEmbeddingModel implements EmbeddingModel {

	public static final int DIMENSIONS = 1024;

	private final AtomicInteger calls = new AtomicInteger();

	private volatile boolean failing;

	/** 打开后每次调用都抛出连接失败，模拟 Ollama 不可用。 */
	public void setFailing(boolean failing) {
		this.failing = failing;
	}

	private final int dimensions;

	public FakeEmbeddingModel() {
		this(DIMENSIONS);
	}

	public FakeEmbeddingModel(int dimensions) {
		this.dimensions = dimensions;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		this.calls.incrementAndGet();
		if (this.failing) {
			throw new org.springframework.web.client.ResourceAccessException("fake embedding model is down");
		}
		List<Embedding> embeddings = new ArrayList<>();
		List<String> inputs = request.getInstructions();
		for (int i = 0; i < inputs.size(); i++) {
			embeddings.add(new Embedding(vector(inputs.get(i), this.dimensions), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(Document document) {
		return vector(document.getText(), this.dimensions);
	}

	@Override
	public int dimensions() {
		return this.dimensions;
	}

	public int calls() {
		return this.calls.get();
	}

	public static float[] vector(String text, int dimensions) {
		float[] vector = new float[dimensions];
		int[] codePoints = (text == null) ? new int[0]
				: text.codePoints().filter(Character::isLetterOrDigit).map(Character::toLowerCase).toArray();
		for (int i = 0; i < codePoints.length; i++) {
			vector[Math.floorMod(Integer.hashCode(codePoints[i]) * 31, dimensions)] += 1f;
			if (i + 1 < codePoints.length) {
				int bigram = codePoints[i] * 65_599 + codePoints[i + 1];
				vector[Math.floorMod(Integer.hashCode(bigram) * 17 + 7, dimensions)] += 2f;
			}
		}
		double norm = 0;
		for (float v : vector) {
			norm += v * v;
		}
		if (norm == 0) {
			vector[0] = 1f;
			return vector;
		}
		float scale = (float) (1 / Math.sqrt(norm));
		for (int i = 0; i < vector.length; i++) {
			vector[i] *= scale;
		}
		return vector;
	}

}
