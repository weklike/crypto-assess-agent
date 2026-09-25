package com.cryptoassess.common.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.RedisTestcontainers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MySQL、Redis、ES 用真实容器；Ollama 和 TEI 用本地 HTTP 桩，只模拟健康检查用到的接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RedisTestcontainers.class, ElasticsearchTestcontainers.class })
class HealthIT {

	private static final AtomicBoolean TEI_HEALTHY = new AtomicBoolean(true);

	private static final HttpServer STUB = startStub();

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void stubUrls(DynamicPropertyRegistry registry) {
		String url = "http://127.0.0.1:" + STUB.getAddress().getPort();
		registry.add("spring.ai.ollama.base-url", () -> url);
		registry.add("app.rerank.base-url", () -> url);
		registry.add("spring.ai.openai.api-key", () -> "test-key-not-used");
	}

	@AfterEach
	void resetStub() {
		TEI_HEALTHY.set(true);
	}

	@AfterAll
	static void stopStub() {
		STUB.stop(0);
	}

	@Test
	void allDependenciesUp() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.mysql.status").value("UP"))
			.andExpect(jsonPath("$.components.redis.status").value("UP"))
			.andExpect(jsonPath("$.components.elasticsearch.status").value("UP"))
			.andExpect(jsonPath("$.components.ollama.status").value("UP"))
			.andExpect(jsonPath("$.components.tei-rerank.status").value("UP"))
			.andExpect(jsonPath("$.components.llm-config.status").value("UP"));
	}

	@Test
	void unavailableRerankServiceGives503() throws Exception {
		TEI_HEALTHY.set(false);

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("DOWN"))
			.andExpect(jsonPath("$.components.tei-rerank.status").value("DOWN"))
			.andExpect(jsonPath("$.components.mysql.status").value("UP"));
	}

	private static HttpServer startStub() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/api/tags",
					exchange -> respond(exchange, 200, "{\"models\":[{\"name\":\"bge-m3:latest\"}]}"));
			server.createContext("/health", exchange -> respond(exchange, TEI_HEALTHY.get() ? 200 : 503, ""));
			server.createContext("/info",
					exchange -> respond(exchange, 200, "{\"model_id\":\"BAAI/bge-reranker-v2-m3\"}"));
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

}
