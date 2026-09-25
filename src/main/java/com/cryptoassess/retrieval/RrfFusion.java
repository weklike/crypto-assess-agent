package com.cryptoassess.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion：score = Σ 1 / (k + rank)。只看名次不看原始分数，
 * 因此 BM25 分和余弦相似度量纲不同也能直接融合。纯函数。
 */
public final class RrfFusion {

	private RrfFusion() {
	}

	/**
	 * @param bm25Rank 没有出现在 BM25 结果中时为 null
	 * @param denseRank 没有出现在向量结果中时为 null
	 */
	public record Fused(String clauseRef, Integer bm25Rank, Integer denseRank, double rrfScore) {
	}

	private static final Comparator<Fused> ORDER = Comparator.comparingDouble(Fused::rrfScore)
		.reversed()
		// 同分时 BM25 名次靠前的优先：关键词精确命中在条款检索里更可靠
		.thenComparing(Fused::bm25Rank, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Fused::denseRank, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Fused::clauseRef);

	public static List<Fused> fuse(List<String> bm25, List<String> dense, int k) {
		if (k <= 0) {
			throw new IllegalArgumentException("RRF k must be positive");
		}
		Map<String, Integer> bm25Ranks = bestRanks(bm25);
		Map<String, Integer> denseRanks = bestRanks(dense);
		Map<String, Fused> fused = new LinkedHashMap<>();
		for (String ref : concat(bm25Ranks.keySet(), denseRanks.keySet())) {
			Integer b = bm25Ranks.get(ref);
			Integer d = denseRanks.get(ref);
			double score = ((b == null) ? 0 : 1.0 / (k + b)) + ((d == null) ? 0 : 1.0 / (k + d));
			fused.put(ref, new Fused(ref, b, d, score));
		}
		List<Fused> result = new ArrayList<>(fused.values());
		result.sort(ORDER);
		return result;
	}

	/** 同一路里重复出现的条款只保留最好（最小）的名次。 */
	private static Map<String, Integer> bestRanks(List<String> ranked) {
		Map<String, Integer> ranks = new LinkedHashMap<>();
		for (int i = 0; i < ranked.size(); i++) {
			ranks.putIfAbsent(ranked.get(i), i + 1);
		}
		return ranks;
	}

	private static List<String> concat(Iterable<String> a, Iterable<String> b) {
		List<String> all = new ArrayList<>();
		a.forEach(all::add);
		b.forEach(ref -> {
			if (!all.contains(ref)) {
				all.add(ref);
			}
		});
		return all;
	}

}
