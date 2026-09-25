package com.cryptoassess.retrieval;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.cryptoassess.knowledge.EmbeddingClient;
import org.springframework.stereotype.Component;

/**
 * 向量检索：查询文本向量化后做 kNN（HNSW 近似），过滤条件在 kNN 内部生效（先过滤再取近邻）。
 */
@Component("denseRetriever")
public class DenseRetriever implements Retriever {

	private final ElasticsearchClient client;

	private final EmbeddingClient embeddingClient;

	private final String index;

	private final int numCandidates;

	public DenseRetriever(ElasticsearchClient client, EmbeddingClient embeddingClient, RetrievalProperties properties) {
		this.client = client;
		this.embeddingClient = embeddingClient;
		this.index = properties.indexAlias();
		this.numCandidates = properties.numCandidates();
	}

	@Override
	public List<Candidate> retrieve(String query, SearchFilter filter, int size) {
		float[] vector = this.embeddingClient.embed(query);
		List<Float> queryVector = new ArrayList<>(vector.length);
		for (float v : vector) {
			queryVector.add(v);
		}
		int candidates = Math.max(this.numCandidates, size);
		return EsQueries.call(() -> EsQueries.toCandidates(this.client.search(s -> s.index(this.index)
			.size(size)
			.source(src -> src.filter(f -> f.includes(List.of(ClauseSource.FIELDS))))
			.knn(k -> k.field("embedding")
				.queryVector(queryVector)
				.k(size)
				.numCandidates(candidates)
				.filter(EsQueries.filters(filter))), ClauseSource.class).hits().hits()));
	}

}
