package com.cryptoassess.support;

import com.cryptoassess.common.audit.AuditService;
import com.cryptoassess.common.security.SecurityConfig;
import com.cryptoassess.common.security.SecurityProperties;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * @WebMvcTest 切片测试用：加载应用自己的安全配置（而不是 Boot 默认的 Basic 认证），审计写入用替身。
 * 管理接口用 {@link #ADMIN_KEY}。
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
public class WebSecurityTestConfiguration {

	public static final String ADMIN_KEY = "k-admin-test-0001";

	public static final String PROPERTY = "app.security.api-keys=admin:" + ADMIN_KEY + ":admin";

	@Bean
	AuditService auditService() {
		return Mockito.mock(AuditService.class);
	}

}
