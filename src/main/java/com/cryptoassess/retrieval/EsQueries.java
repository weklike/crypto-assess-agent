package com.cryptoassess.retrieval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.cryptoassess.common.error.AppException;

final class EsQueries {

	private EsQueries() {
	}

	/** 层面、等级过滤，BM25 与 kNN 共用同一组条件。 */
	static List<Query> filters(SearchFilter filter) {
		List<Query> filters = new ArrayList<>();
		if (filter.layer() != null) {
			filters.add(Query.of(q -> q.term(t -> t.field("layer").value(filter.layer()))));
		}
		if (filter.level() != null) {
			filters.add(Query.of(q -> q.term(t -> t.field("levels").value(String.valueOf(filter.level())))));
		}
		return filters;
	}

	static List<Candidate> toCandidates(List<Hit<ClauseSource>> hits) {
		List<Candidate> candidates = new ArrayList<>(hits.size());
		for (int i = 0; i < hits.size(); i++) {
			Hit<ClauseSource> hit = hits.get(i);
			ClauseSource source = hit.source();
			candidates.add(new Candidate(source.clauseRef(), source.clauseNo() + " " + source.title(), source.path(),
					source.body(), source.layer(), (hit.score() == null) ? 0 : hit.score(), i + 1));
		}
		return candidates;
	}

	static AppException unavailable(Exception ex) {
		if (ex instanceof ElasticsearchException es && es.status() == 404) {
			return new AppException(com.cryptoassess.common.error.ErrorType.DEPENDENCY_UNAVAILABLE,
					"clause index not found, run POST /api/kb/reindex first", ex);
		}
		return AppException.unavailable("elasticsearch", ex);
	}

	@FunctionalInterface
	interface EsCall<T> {

		T call() throws IOException;

	}

	static <T> T call(EsCall<T> call) {
		try {
			return call.call();
		}
		catch (IOException | ElasticsearchException ex) {
			throw unavailable(ex);
		}
	}

}
