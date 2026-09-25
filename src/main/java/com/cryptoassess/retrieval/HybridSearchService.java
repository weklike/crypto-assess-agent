package com.cryptoassess.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cryptoassess.retrieval.RrfFusion.Fused;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 四种检索模式：bm25、dense、hybrid（RRF 融合）、hybrid_rerank（融合后前 N 条交给重排）。
 * 任一依赖失败都向上抛，不从 hybrid_rerank 悄悄退回 hybrid。
 */
@Service
public class HybridSearchService {

	private static final int SNIPPET_LENGTH = 160;

	private final Retriever bm25Retriever;

	private final Retriever denseRetriever;

	private final RerankClient rerankClient;

	private final RetrievalProperties properties;

	private final RerankProperties rerankProperties;

	private final ObservationRegistry observations;

	public HybridSearchService(@Qualifier("bm25Retriever") Retriever bm25Retriever,
			@Qualifier("denseRetriever") Retriever denseRetriever, RerankClient rerankClient,
			RetrievalProperties properties, RerankProperties rerankProperties, ObservationRegistry observations) {
		this.bm25Retriever = bm25Retriever;
		this.denseRetriever = denseRetriever;
		this.rerankClient = rerankClient;
		this.properties = properties;
		this.rerankProperties = rerankProperties;
		this.observations = observations;
	}

	/**
	 * 每个阶段一个 span（retrieval.search / bm25 / dense / fusion / rerank），只带模式和条数，不带查询文本。
	 */
	public SearchResponse search(SearchRequest request) {
		return Observation.createNotStarted("retrieval.search", this.observations)
			.lowCardinalityKeyValue("retrieval.mode", request.effectiveMode().value())
			.highCardinalityKeyValue("retrieval.k", String.valueOf(request.effectiveK()))
			.observe(() -> doSearch(request));
	}

	private SearchResponse doSearch(SearchRequest request) {
		long start = System.nanoTime();
		SearchMode mode = request.effectiveMode();
		int k = request.effectiveK();
		SearchFilter filter = request.filter();
		Map<String, Long> timings = new LinkedHashMap<>();
		List<SearchHit> hits = switch (mode) {
			case BM25 -> single(timed(timings, "bm25Ms",
					() -> observed("retrieval.bm25", () -> this.bm25Retriever.retrieve(request.query(), filter, k))), true);
			case DENSE -> single(timed(timings, "denseMs",
					() -> observed("retrieval.dense", () -> this.denseRetriever.retrieve(request.query(), filter, k))),
					false);
			case HYBRID -> fused(request.query(), filter, timings).stream().limit(k).map(Scored::toHit).toList();
			case HYBRID_RERANK -> reranked(request.query(), fused(request.query(), filter, timings), k, timings);
		};
		timings.put("totalMs", millisSince(start));
		return new SearchResponse(mode, request.query(), k, hits, timings);
	}

	private List<Scored> fused(String query, SearchFilter filter, Map<String, Long> timings) {
		int size = this.properties.candidates();
		List<Candidate> bm25;
		List<Candidate> dense;
		// 两路并行：总耗时取决于较慢的一路（通常是查询向量化）
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Map<String, Long> bm25Timing = new HashMap<>();
			Map<String, Long> denseTiming = new HashMap<>();
			Observation parent = this.observations.getCurrentObservation();
			Future<List<Candidate>> bm25Future = executor.submit(() -> timed(bm25Timing, "bm25Ms",
					() -> observed("retrieval.bm25", parent, () -> this.bm25Retriever.retrieve(query, filter, size))));
			Future<List<Candidate>> denseFuture = executor.submit(() -> timed(denseTiming, "denseMs",
					() -> observed("retrieval.dense", parent, () -> this.denseRetriever.retrieve(query, filter, size))));
			bm25 = join(bm25Future);
			dense = join(denseFuture);
			timings.putAll(bm25Timing);
			timings.putAll(denseTiming);
		}
		return observed("retrieval.fusion", () -> fuse(bm25, dense, timings));
	}

	private List<Scored> fuse(List<Candidate> bm25, List<Candidate> dense, Map<String, Long> timings) {
		long fusionStart = System.nanoTime();
		Map<String, Candidate> byRef = new HashMap<>();
		bm25.forEach(c -> byRef.putIfAbsent(c.clauseRef(), c));
		dense.forEach(c -> byRef.putIfAbsent(c.clauseRef(), c));
		Map<String, Candidate> bm25ByRef = index(bm25);
		Map<String, Candidate> denseByRef = index(dense);
		List<Fused> fused = RrfFusion.fuse(bm25.stream().map(Candidate::clauseRef).toList(),
				dense.stream().map(Candidate::clauseRef).toList(), this.properties.rrfK());
		List<Scored> result = new ArrayList<>(fused.size());
		for (Fused f : fused) {
			result.add(new Scored(byRef.get(f.clauseRef()), bm25ByRef.get(f.clauseRef()),
					denseByRef.get(f.clauseRef()), f.rrfScore(), null));
		}
		timings.put("fusionMs", millisSince(fusionStart));
		return result;
	}

	private List<SearchHit> reranked(String query, List<Scored> fused, int k, Map<String, Long> timings) {
		List<Scored> head = fused.stream().limit(this.rerankProperties.topN()).toList();
		double[] scores = timed(timings, "rerankMs", () -> observed("retrieval.rerank",
				() -> this.rerankClient.rerank(query, head.stream().map(s -> rerankText(s.candidate())).toList())));
		List<Scored> rescored = new ArrayList<>(head.size());
		for (int i = 0; i < head.size(); i++) {
			rescored.add(head.get(i).withRerank(scores[i]));
		}
		// 稳定排序：重排分相同时保留 RRF 顺序
		rescored.sort(Comparator.comparingDouble(Scored::rerankScore).reversed());
		return rescored.stream().limit(k).map(Scored::toHit).toList();
	}

	private static List<SearchHit> single(List<Candidate> candidates, boolean bm25) {
		return candidates.stream()
			.map(c -> new Scored(c, bm25 ? c : null, bm25 ? null : c, null, null).toHit())
			.toList();
	}

	private <T> T observed(String name, java.util.function.Supplier<T> action) {
		return observed(name, this.observations.getCurrentObservation(), action);
	}

	/** 在指定父 span 下观测一段调用；两路并行检索跑在虚拟线程上，需要显式传入父 span。 */
	private <T> T observed(String name, Observation parent, java.util.function.Supplier<T> action) {
		Observation observation = Observation.createNotStarted(name, this.observations);
		if (parent != null) {
			observation.parentObservation(parent);
		}
		return observation.observe(() -> {
			T result = action.get();
			if (result instanceof List<?> list) {
				observation.highCardinalityKeyValue("retrieval.results", String.valueOf(list.size()));
			}
			return result;
		});
	}

	static String rerankText(Candidate candidate) {
		return candidate.path() + "\n" + candidate.title() + "\n" + candidate.body();
	}

	static String snippet(String body) {
		if (body == null) {
			return "";
		}
		return (body.length() <= SNIPPET_LENGTH) ? body : body.substring(0, SNIPPET_LENGTH) + "…";
	}

	private static Map<String, Candidate> index(List<Candidate> candidates) {
		Map<String, Candidate> byRef = new HashMap<>();
		candidates.forEach(c -> byRef.putIfAbsent(c.clauseRef(), c));
		return byRef;
	}

	private static <T> T join(Future<T> future) {
		try {
			return future.get();
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException(ex.getCause());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted during retrieval", ex);
		}
	}

	private static <T> T timed(Map<String, Long> timings, String key, java.util.function.Supplier<T> action) {
		long start = System.nanoTime();
		try {
			return action.get();
		}
		finally {
			timings.put(key, millisSince(start));
		}
	}

	private static long millisSince(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

	private record Scored(Candidate candidate, Candidate bm25, Candidate dense, Double rrfScore, Double rerankScore) {

		Scored withRerank(double score) {
			return new Scored(this.candidate, this.bm25, this.dense, this.rrfScore, score);
		}

		SearchHit toHit() {
			return new SearchHit(this.candidate.clauseRef(), this.candidate.title(), this.candidate.path(),
					this.candidate.layer(), snippet(this.candidate.body()),
					(this.bm25 == null) ? null : this.bm25.rank(), (this.bm25 == null) ? null : this.bm25.score(),
					(this.dense == null) ? null : this.dense.rank(), (this.dense == null) ? null : this.dense.score(),
					this.rrfScore, this.rerankScore);
		}

	}

}
