package com.cryptoassess.retrieval;

/**
 * 同时作用于 BM25 与向量检索的过滤条件，均可空。
 *
 * @param layer 安全层面
 * @param level 等级（1-4），匹配 levels 中包含该等级的条款
 */
public record SearchFilter(String layer, Integer level) {

	public static final SearchFilter NONE = new SearchFilter(null, null);

}
