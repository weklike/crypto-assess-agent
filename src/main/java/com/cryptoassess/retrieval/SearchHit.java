package com.cryptoassess.retrieval;

/**
 * 一条检索结果，带各阶段的名次与分数；某阶段未参与或未命中时对应字段为 null。
 */
public record SearchHit(String clauseRef, String title, String path, String layer, String snippet, Integer bm25Rank,
		Double bm25Score, Integer denseRank, Double denseScore, Double rrfScore, Double rerankScore) {

}
