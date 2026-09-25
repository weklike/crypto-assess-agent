package com.cryptoassess.common.llm;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一次模型调用的记录。token 数在服务端没有返回时为 null。
 */
public record LlmCall(String purpose, String model, String promptVersion, Integer latencyMs, Integer inputTokens,
		Integer outputTokens, BigDecimal costCny, LlmCallStatus status, String errorCode, Instant createdAt) {

}
