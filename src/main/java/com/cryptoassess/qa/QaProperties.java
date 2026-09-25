package com.cryptoassess.qa;

import java.time.Duration;

import com.cryptoassess.retrieval.SearchMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param topK 提供给模型的条款数
 * @param mode 默认检索模式
 * @param timeout 模型调用总超时，超时返回 error 事件，不重试
 * @param promptVersion 提示词文件名（不含 .st）
 * @param temperature 采样温度，为空时用模型默认值
 */
@Validated
@ConfigurationProperties("app.qa")
public record QaProperties(@Positive int topK, @NotNull SearchMode mode, @NotNull Duration timeout,
		@NotBlank String promptVersion, Double temperature) {

}
