package com.cryptoassess.common.health;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cryptoassess.support.WebSecurityTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = HealthController.class, properties = WebSecurityTestConfiguration.PROPERTY)
@Import(WebSecurityTestConfiguration.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HealthService healthService;

	@Test
	void returns200WhenAllUp() throws Exception {
		given(healthService.check()).willReturn(report(CheckResult.up("ok")));

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.mysql.status").value("UP"));
	}

	@Test
	void returns503WithDetailsWhenAnyDown() throws Exception {
		given(healthService.check()).willReturn(report(CheckResult.down("connection refused")));

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("DOWN"))
			.andExpect(jsonPath("$.components.tei.detail").value("connection refused"));
	}

	private static HealthReport report(CheckResult tei) {
		Map<String, CheckResult> components = new LinkedHashMap<>();
		components.put("mysql", CheckResult.up("ok"));
		components.put("tei", tei);
		return HealthReport.of(components, Instant.parse("2026-09-24T00:00:00Z"));
	}

}
