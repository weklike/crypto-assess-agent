package com.cryptoassess.common.llm;

/**
 * @param latencyMs 模型调用耗时
 */
public record StructuredResult<T>(T value, long llmCallId, String model, Integer inputTokens, Integer outputTokens,
		long latencyMs) {

}
