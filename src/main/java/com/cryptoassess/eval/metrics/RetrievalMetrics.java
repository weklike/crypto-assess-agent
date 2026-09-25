package com.cryptoassess.eval.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索指标，二元相关性（在 gold_refs 里即相关）。纯函数，自己实现以便讲清楚每个公式。
 */
public final class RetrievalMetrics {

	private RetrievalMetrics() {
	}

	/** Recall@k = 前 k 个位置中出现的相关条款数 / 相关条款总数。 */
	public static double recallAt(List<String> ranked, Set<String> gold, int k) {
		if (gold.isEmpty()) {
			return 0;
		}
		Set<String> found = new HashSet<>(ranked.subList(0, Math.min(k, ranked.size())));
		found.retainAll(gold);
		return (double) found.size() / gold.size();
	}

	/** 第一个相关结果名次的倒数；前 k 个里没有相关结果时为 0。对查询取平均即 MRR@k。 */
	public static double reciprocalRank(List<String> ranked, Set<String> gold, int k) {
		for (int i = 0; i < Math.min(k, ranked.size()); i++) {
			if (gold.contains(ranked.get(i))) {
				return 1.0 / (i + 1);
			}
		}
		return 0;
	}

	/**
	 * nDCG@k：DCG = Σ rel_i / log2(i + 1)，i 从 1 开始；IDCG 为全部相关条款排在最前时的 DCG。
	 * 同一条款重复出现时只在第一次计分。
	 */
	public static double ndcgAt(List<String> ranked, Set<String> gold, int k) {
		if (gold.isEmpty()) {
			return 0;
		}
		double dcg = 0;
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < Math.min(k, ranked.size()); i++) {
			String ref = ranked.get(i);
			if (gold.contains(ref) && seen.add(ref)) {
				dcg += 1 / log2(i + 2);
			}
		}
		double idcg = 0;
		for (int i = 0; i < Math.min(k, gold.size()); i++) {
			idcg += 1 / log2(i + 2);
		}
		return dcg / idcg;
	}

	private static double log2(double x) {
		return Math.log(x) / Math.log(2);
	}

}
