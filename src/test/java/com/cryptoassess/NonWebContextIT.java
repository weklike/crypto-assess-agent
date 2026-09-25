package com.cryptoassess;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptoassess.eval.EvalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * eval profile 不启动 Web 服务：安全配置等只属于 Web 的组件不能阻止非 Web 上下文启动。
 * （不激活 eval profile 本身，因为 EvalRunner 跑完会退出进程。）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.main.web-application-type=none", "app.workflow.resume-on-startup=false" })
@Import(TestcontainersConfiguration.class)
class NonWebContextIT {

	@Autowired
	private EvalService evalService;

	@Test
	void contextStartsWithoutWebServer() {
		assertThat(evalService).isNotNull();
	}

}
