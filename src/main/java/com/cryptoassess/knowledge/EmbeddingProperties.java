package com.cryptoassess.knowledge;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param model embedding 模型名（与 spring.ai.ollama.embedding.model 一致，写进评测报告和缓存键）
 * @param dimensions 期望的向量维度，不一致直接报错
 * @param batchSize 每批向量化的文本数
 * @param timeout 单批向量化超时
 */
@Validated
@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(@NotBlank String model, @Positive int dimensions, @Positive int batchSize,
		@NotNull Duration timeout) {

}
