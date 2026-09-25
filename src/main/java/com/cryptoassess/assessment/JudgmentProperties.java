package com.cryptoassess.assessment;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param timeout 单次判定的模型调用超时
 * @param temperature 采样温度，默认 0
 */
@Validated
@ConfigurationProperties("app.judge")
public record JudgmentProperties(@NotNull Duration timeout, Double temperature) {

}
