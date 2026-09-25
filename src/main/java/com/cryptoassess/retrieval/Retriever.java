package com.cryptoassess.retrieval;

import java.util.List;

/**
 * 单路检索（BM25 或向量），返回按名次排列的候选。
 */
@FunctionalInterface
public interface Retriever {

	List<Candidate> retrieve(String query, SearchFilter filter, int size);

}
