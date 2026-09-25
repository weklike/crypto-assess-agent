package com.cryptoassess.common.health;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param timeout 单个依赖检查的超时，超时视为不可用
 */
@Validated
@ConfigurationProperties("app.health")
public record HealthProperties(@NotNull Duration timeout) {

}
