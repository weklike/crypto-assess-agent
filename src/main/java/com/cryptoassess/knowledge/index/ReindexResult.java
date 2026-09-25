package com.cryptoassess.knowledge.index;

public record ReindexResult(String indexName, String alias, long documentCount, String embeddingModel, long tookMs) {

}
