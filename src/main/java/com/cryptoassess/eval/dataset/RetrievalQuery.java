package com.cryptoassess.eval.dataset;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 检索评测集的一行（eval/datasets/retrieval_queries.vN.jsonl）。
 *
 * @param goldRefs 应当命中的条款引用
 * @param style colloquial（口语）/ paraphrase（改写）/ keyword（关键词）
 * @param layer 查询所属安全层面，可空；只用于分组统计，不作为检索过滤条件
 * @param level 等级，可空
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RetrievalQuery(String id, String query, @JsonProperty("gold_refs") List<String> goldRefs, String style,
		String layer, Integer level, String note) {

	public static final List<String> STYLES = List.of("colloquial", "paraphrase", "keyword");

}
