package com.cryptoassess.knowledge;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param normalizedDir 规范化标准文件所在目录，导入接口只接受这个目录下的文件名
 */
@Validated
@ConfigurationProperties("app.knowledge")
public record KnowledgeProperties(@NotBlank String normalizedDir) {

}
