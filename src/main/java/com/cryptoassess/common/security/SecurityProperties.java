package com.cryptoassess.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKeys 格式 {@code 客户端:密钥:scope1,scope2;…}，来自环境变量 MCP_API_KEYS；为空时 MCP 与管理接口全部拒绝
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(String apiKeys) {

}
