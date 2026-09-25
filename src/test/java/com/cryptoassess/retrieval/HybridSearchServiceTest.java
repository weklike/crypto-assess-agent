package com.cryptoassess.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.support.FakeRerankClient;
import org.junit.jupiter.api.Test;

class HybridSearchServiceTest {

	private static final RetrievalProperties PROPS = new RetrievalProperties("kb_clause", 50, 200, 60, 0.1);

	private static final RerankProperties RERANK = new RerankProperties("http://tei", "m", Duration.ofSeconds(1), 30);

	private final List<SearchFilter> seenFilters = new ArrayList<>();

	private final List<Integer> seenSizes = new ArrayList<>();

	private final FakeRerankClient rerank = new FakeRerankClient();

	private Retriever retriever(List<Candidate> result) {
		return (query, filter, size) -> {
			seenFilters.add(filter);
			seenSizes.add(size);
			return result.stream().limit(size).toList();
		};
	}

	private static Candidate candidate(String ref, int rank, String body) {
		return new Candidate("FIX/T 0001-2026#" + ref, ref + " 标题", "路径", body, "网络和通信", 10.0 / rank, rank);
	}

	private HybridSearchService service(List<Candidate> bm25, List<Candidate> dense) {
		return new HybridSearchService(retriever(bm25), retriever(dense), rerank, PROPS, RERANK,
				io.micrometer.observation.ObservationRegistry.NOOP);
	}

	@Test
	void bm25ModeUsesOnlyBm25AndCutsToK() {
		HybridSearchService service = service(List.of(candidate("1", 1, "a"), candidate("2", 2, "b")), List.of());

		SearchResponse response = service.search(new SearchRequest("q", SearchMode.BM25, 1, null, null));

		assertThat(response.hits()).extracting(SearchHit::clauseRef).containsExactly("FIX/T 0001-2026#1");
		assertThat(response.hits().get(0).bm25Rank()).isEqualTo(1);
		assertThat(response.hits().get(0).denseRank()).isNull();
		assertThat(response.timings()).containsKeys("bm25Ms", "totalMs").doesNotContainKey("denseMs");
	}

	@Test
	void hybridFusesBothListsWithSameFilterAndCandidateCount() {
		HybridSearchService service = service(List.of(candidate("1", 1, "a"), candidate("2", 2, "b")),
				List.of(candidate("2", 1, "b"), candidate("3", 2, "c")));

		SearchResponse response = service.search(new SearchRequest("q", SearchMode.HYBRID, 10, "网络和通信", 3));

		assertThat(response.hits()).extracting(SearchHit::clauseRef)
			.containsExactly("FIX/T 0001-2026#2", "FIX/T 0001-2026#1", "FIX/T 0001-2026#3");
		assertThat(seenFilters).containsOnly(new SearchFilter("网络和通信", 3));
		assertThat(seenSizes).containsOnly(50);
		assertThat(response.hits().get(0).rrfScore()).isNotNull();
		assertThat(response.timings()).containsKeys("bm25Ms", "denseMs", "fusionMs", "totalMs");
	}

	@Test
	void hybridRerankOnlyRerankTopNAndOrdersByRerankScore() {
		List<Candidate> bm25 = IntStream.rangeClosed(1, 40)
			.mapToObj(i -> candidate(String.valueOf(i), i, (i == 40) ? "身份鉴别 密钥" : "无关"))
			.toList();
		List<Candidate> dense = List.of(candidate("40", 1, "身份鉴别 密钥"));
		HybridSearchService service = service(bm25, dense);

		SearchResponse response = service.search(new SearchRequest("身份鉴别", SearchMode.HYBRID_RERANK, 5, null, null));

		assertThat(response.hits()).hasSize(5);
		assertThat(response.hits().get(0).clauseRef()).isEqualTo("FIX/T 0001-2026#40");
		assertThat(response.hits().get(0).rerankScore()).isEqualTo(1.0);
		assertThat(response.timings()).containsKey("rerankMs");
		assertThat(rerank.calls()).isEqualTo(1);
	}

	@Test
	void rerankUnavailableIsNotSilentlyDegraded() {
		rerank.setAvailable(false);
		HybridSearchService service = service(List.of(candidate("1", 1, "a")), List.of());

		assertThatThrownBy(() -> service.search(new SearchRequest("q", SearchMode.HYBRID_RERANK, 5, null, null)))
			.isInstanceOf(AppException.class);
	}

	@Test
	void snippetIsTruncated() {
		HybridSearchService service = service(List.of(candidate("1", 1, "密".repeat(500))), List.of());

		SearchHit hit = service.search(new SearchRequest("q", SearchMode.BM25, 5, null, null)).hits().get(0);

		assertThat(hit.snippet().length()).isLessThanOrEqualTo(161);
	}

}
