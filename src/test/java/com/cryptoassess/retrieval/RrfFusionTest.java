package com.cryptoassess.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;

import com.cryptoassess.retrieval.RrfFusion.Fused;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RrfFusionTest {

	private static final int K = 60;

	static Stream<Arguments> orderingCases() {
		return Stream.of(
				Arguments.of("两路都为空", List.of(), List.of(), List.of()),
				Arguments.of("只有 BM25", List.of("a", "b"), List.of(), List.of("a", "b")),
				Arguments.of("只有向量", List.of(), List.of("x", "y", "z"), List.of("x", "y", "z")),
				// c=1/63+1/61 最高；b 与 d 都是 1/62，b 有 BM25 名次排前
				Arguments.of("两路都命中的条款排前", List.of("a", "b", "c"), List.of("c", "d"), List.of("c", "a", "b", "d")),
				// a: 1/61，d: 1/61 并列，按 BM25 名次：a 有 BM25 名次，d 没有
				Arguments.of("并列时 BM25 名次优先", List.of("a"), List.of("d"), List.of("a", "d")),
				// b 在 BM25 第 2、向量第 1；a 在 BM25 第 1、向量第 2：分数相同，BM25 名次高的 a 在前
				Arguments.of("交叉并列", List.of("a", "b"), List.of("b", "a"), List.of("a", "b")),
				Arguments.of("同一路内的重复条款只算最好名次", List.of("a", "a", "b"), List.of(), List.of("a", "b")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("orderingCases")
	void fusesByReciprocalRank(String name, List<String> bm25, List<String> dense, List<String> expected) {
		List<Fused> fused = RrfFusion.fuse(bm25, dense, K);

		assertThat(fused).extracting(Fused::clauseRef).containsExactlyElementsOf(expected);
	}

	@Test
	void scoreIsSumOfReciprocalRanksAndRanksAreKept() {
		List<Fused> fused = RrfFusion.fuse(List.of("a", "b", "c"), List.of("c", "d"), K);

		Fused c = fused.get(0);
		assertThat(c.clauseRef()).isEqualTo("c");
		assertThat(c.bm25Rank()).isEqualTo(3);
		assertThat(c.denseRank()).isEqualTo(1);
		assertThat(c.rrfScore()).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
		Fused d = fused.stream().filter(f -> f.clauseRef().equals("d")).findFirst().orElseThrow();
		assertThat(d.bm25Rank()).isNull();
		assertThat(d.denseRank()).isEqualTo(2);
	}

	@Test
	void smallerKMakesTopRanksDominate() {
		// k=1：单路第 1 名 1/2=0.5 胜过两路都第 4 名 2/5=0.4；k=60：1/61 输给 2/64
		List<String> bm25 = List.of("top", "x", "w", "both");
		List<String> dense = List.of("y", "z", "v", "both");

		assertThat(RrfFusion.fuse(bm25, dense, 60).get(0).clauseRef()).isEqualTo("both");
		assertThat(RrfFusion.fuse(bm25, dense, 1).get(0).clauseRef()).isEqualTo("top");
	}

}
