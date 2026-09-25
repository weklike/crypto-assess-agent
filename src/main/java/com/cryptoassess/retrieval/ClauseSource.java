package com.cryptoassess.retrieval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 检索时从 _source 读取的字段（不含向量）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ClauseSource(@JsonProperty("clause_ref") String clauseRef, @JsonProperty("clause_no") String clauseNo,
		@JsonProperty("title") String title, @JsonProperty("path") String path, @JsonProperty("body") String body,
		@JsonProperty("layer") String layer) {

	static final String[] FIELDS = { "clause_ref", "clause_no", "title", "path", "body", "layer" };

}
