package com.cryptoassess.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SearchMode {

	@JsonProperty("bm25")
	BM25,

	@JsonProperty("dense")
	DENSE,

	@JsonProperty("hybrid")
	HYBRID,

	@JsonProperty("hybrid_rerank")
	HYBRID_RERANK;

	public String value() {
		return name().toLowerCase();
	}

	public static SearchMode fromValue(String value) {
		return valueOf(value.toUpperCase());
	}

}
