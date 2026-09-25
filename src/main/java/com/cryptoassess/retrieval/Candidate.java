package com.cryptoassess.retrieval;

/**
 * 单路检索的一条结果。
 *
 * @param score 该路的原始分数（BM25 分或向量相似度）
 * @param rank 该路名次，从 1 开始
 */
public record Candidate(String clauseRef, String title, String path, String body, String layer, double score,
		int rank) {

}
