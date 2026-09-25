package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;

import com.cryptoassess.eval.metrics.Percentiles;
import com.cryptoassess.eval.metrics.RetrievalMetrics;
import org.junit.jupiter.api.Test;

class RetrievalMetricsTest {

	private static final List<String> RANKED = List.of("a", "b", "c", "d", "e", "f");

	@Test
	void recallAtK() {
		// gold = {b, e, z}：前 1 条命中 0 个，前 3 条命中 b，前 5 条命中 b、e
		Set<String> gold = Set.of("b", "e", "z");

		assertThat(RetrievalMetrics.recallAt(RANKED, gold, 1)).isEqualTo(0.0);
		assertThat(RetrievalMetrics.recallAt(RANKED, gold, 3)).isCloseTo(1.0 / 3, within(1e-12));
		assertThat(RetrievalMetrics.recallAt(RANKED, gold, 5)).isCloseTo(2.0 / 3, within(1e-12));
		assertThat(RetrievalMetrics.recallAt(List.of(), gold, 5)).isEqualTo(0.0);
	}

	@Test
	void reciprocalRankUsesFirstRelevantWithinCutoff() {
		assertThat(RetrievalMetrics.reciprocalRank(RANKED, Set.of("c", "a"), 10)).isEqualTo(1.0);
		assertThat(RetrievalMetrics.reciprocalRank(RANKED, Set.of("c"), 10)).isCloseTo(1.0 / 3, within(1e-12));
		assertThat(RetrievalMetrics.reciprocalRank(RANKED, Set.of("f"), 5)).isEqualTo(0.0);
		assertThat(RetrievalMetrics.reciprocalRank(RANKED, Set.of("z"), 10)).isEqualTo(0.0);
	}

	@Test
	void ndcgWithBinaryRelevance() {
		// gold = {a, c}：DCG = 1/log2(2) + 1/log2(4) = 1 + 0.5 = 1.5；IDCG = 1 + 1/log2(3) ≈ 1.63093
		double expected = 1.5 / (1 + 1 / (Math.log(3) / Math.log(2)));

		assertThat(RetrievalMetrics.ndcgAt(RANKED, Set.of("a", "c"), 10)).isCloseTo(expected, within(1e-12));
		assertThat(RetrievalMetrics.ndcgAt(RANKED, Set.of("a"), 10)).isEqualTo(1.0);
		assertThat(RetrievalMetrics.ndcgAt(RANKED, Set.of("z"), 10)).isEqualTo(0.0);
		// 截断：f 在第 6 位，k=5 时不计
		assertThat(RetrievalMetrics.ndcgAt(RANKED, Set.of("f"), 5)).isEqualTo(0.0);
	}

	@Test
	void duplicateRefsInRankingCountOnce() {
		assertThat(RetrievalMetrics.recallAt(List.of("a", "a", "b"), Set.of("a", "b"), 2)).isEqualTo(0.5);
	}

	@Test
	void nearestRankPercentiles() {
		List<Long> latencies = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);

		assertThat(Percentiles.nearestRank(latencies, 50)).isEqualTo(50L);
		assertThat(Percentiles.nearestRank(latencies, 95)).isEqualTo(100L);
		assertThat(Percentiles.nearestRank(List.of(7L), 95)).isEqualTo(7L);
		assertThat(Percentiles.nearestRank(List.of(), 95)).isNull();
	}

}
