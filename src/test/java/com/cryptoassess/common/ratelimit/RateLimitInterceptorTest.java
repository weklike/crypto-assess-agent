package com.cryptoassess.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import com.cryptoassess.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class RateLimitInterceptorTest {

	@RestController
	static class Dummy {

		@PostMapping("/api/qa")
		String qa() {
			return "ok";
		}

	}

	@Test
	void rejectedRequestGets429WithRetryAfter() throws Exception {
		List<String> keys = new ArrayList<>();
		RateLimiter limiter = key -> {
			keys.add(key);
			return keys.size() <= 1 ? new RateLimiter.Decision(true, 0, 0) : new RateLimiter.Decision(false, 0, 2500);
		};
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new Dummy())
			.addMappedInterceptors(new String[] { "/api/qa" }, new RateLimitInterceptor(limiter))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

		mvc.perform(post("/api/qa").with(r -> {
			r.setRemoteAddr("10.0.0.7");
			return r;
		})).andExpect(status().isOk());
		mvc.perform(post("/api/qa").with(r -> {
			r.setRemoteAddr("10.0.0.7");
			return r;
		}))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "3"))
			.andExpect(jsonPath("$.type").value("urn:crypto-assess:error:rate-limited"));
		org.assertj.core.api.Assertions.assertThat(keys).containsOnly("ip:10.0.0.7");
	}

}
