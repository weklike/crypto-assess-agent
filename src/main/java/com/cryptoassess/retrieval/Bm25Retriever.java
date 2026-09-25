package com.cryptoassess.retrieval;

import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.stereotype.Component;

/**
 * BM25：multi_match 查 title（权重 2）、body、path，查询端用 ik_smart。
 */
@Component("bm25Retriever")
public class Bm25Retriever implements Retriever {

	private final ElasticsearchClient client;

	private final String index;

	public Bm25Retriever(ElasticsearchClient client, RetrievalProperties properties) {
		this.client = client;
		this.index = properties.indexAlias();
	}

	@Override
	public List<Candidate> retrieve(String query, SearchFilter filter, int size) {
		return EsQueries.call(() -> EsQueries.toCandidates(this.client.search(s -> s.index(this.index)
			.size(size)
			.source(src -> src.filter(f -> f.includes(List.of(ClauseSource.FIELDS))))
			.query(q -> q.bool(b -> b
				.must(m -> m.multiMatch(mm -> mm.query(query).fields("title^2", "body", "path")))
				.filter(EsQueries.filters(filter)))), ClauseSource.class).hits().hits()));
	}

}
