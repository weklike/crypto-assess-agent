package com.cryptoassess.retrieval;

import java.util.List;
import java.util.Map;

/**
 * @param timings 各阶段耗时（毫秒），键如 bm25Ms、denseMs、fusionMs、rerankMs、totalMs
 */
public record SearchResponse(SearchMode mode, String query, int k, List<SearchHit> hits, Map<String, Long> timings) {

}
