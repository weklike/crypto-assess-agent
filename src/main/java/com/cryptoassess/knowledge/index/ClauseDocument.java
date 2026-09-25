package com.cryptoassess.knowledge.index;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ES 索引 kb_clause_v1 的文档结构，字段名与 es/kb_clause_v1.json 一致。
 */
public record ClauseDocument(@JsonProperty("clause_ref") String clauseRef, @JsonProperty("doc_code") String docCode,
		@JsonProperty("clause_no") String clauseNo, @JsonProperty("path") String path, @JsonProperty("title") String title,
		@JsonProperty("body") String body, @JsonProperty("layer") String layer,
		@JsonProperty("levels") List<String> levels, @JsonProperty("clause_type") String clauseType,
		@JsonProperty("embedding") float[] embedding) {

}
