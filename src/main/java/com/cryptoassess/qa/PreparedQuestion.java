package com.cryptoassess.qa;

import com.cryptoassess.retrieval.SearchResponse;

/**
 * 检索和拒答判断完成后的问题，交给流式阶段使用。
 *
 * @param topScore 用于拒答判断的最高分（hybrid_rerank 为重排分）
 */
public record PreparedQuestion(QaRequest request, SearchResponse search, Double topScore, boolean refused) {

}
