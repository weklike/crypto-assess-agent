package com.cryptoassess.common.llm;

import java.time.Duration;

/**
 * 一次结构化输出调用。
 *
 * @param purpose 写进 llm_call.purpose，如 exam、judge
 * @param promptVersion 提示词版本
 * @param prompt 已渲染的提示词全文（不写日志、不落库）
 * @param schema src/main/resources/schemas/ 下的 JSON Schema 文件名（不含 .json）
 * @param temperature 采样温度，为空时用模型默认值
 * @param timeout 调用超时，超时即失败，不重试
 */
public record StructuredRequest(String purpose, String promptVersion, String prompt, String schema, Double temperature,
		Duration timeout) {

}
