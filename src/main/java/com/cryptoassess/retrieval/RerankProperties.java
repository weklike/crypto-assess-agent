package com.cryptoassess.retrieval;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl TEI 服务地址
 * @param model TEI 应当加载的重排模型，健康检查据此核对
 * @param timeout 单次重排请求超时
 * @param topN 送去重排的融合结果条数
 */
@Validated
@ConfigurationProperties("app.rerank")
public record RerankProperties(@NotBlank String baseUrl, @NotBlank String model, @NotNull Duration timeout,
		@Positive int topN) {

}
