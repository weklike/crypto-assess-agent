package com.cryptoassess.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeRerankConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "app.knowledge.normalized-dir=data/fixtures", "app.workflow.resume-on-startup=false",
				"app.security.api-keys=full:k-full-000000001:kb:read,rules:read;rules-only:k-rules-00000001:rules:read" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class McpIT {

	private static final String CALL_SEARCH = """
			{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_clauses","arguments":{"query":"密钥"}}}""";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeAll
	void indexFixture(@Autowired KnowledgeIngestService ingestService, @Autowired ClauseIndexer indexer) {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		ingestService.importDocument("mock-standard.md");
		indexer.reindex();
	}

	private HttpResponse<String> post(String key, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (key != null) {
			request.header("X-API-Key", key);
		}
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private McpSyncClient client(String key) {
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
			.builder("http://127.0.0.1:" + port)
			.endpoint("/mcp")
			.requestBuilder(HttpRequest.newBuilder().header("X-API-Key", key))
			.build();
		McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
		client.initialize();
		return client;
	}

	private static String text(McpSchema.CallToolResult result) {
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

	@Test
	void missingOrWrongKeyIs401() throws Exception {
		assertThat(post(null, CALL_SEARCH).statusCode()).isEqualTo(401);
		HttpResponse<String> wrong = post("k-wrong-00000000", CALL_SEARCH);
		assertThat(wrong.statusCode()).isEqualTo(401);
		assertThat(wrong.body()).contains("urn:crypto-assess:error:unauthorized");
	}

	@Test
	void insufficientScopeIs403AndAudited() throws Exception {
		HttpResponse<String> response = post("k-rules-00000001", CALL_SEARCH);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains("kb:read");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM audit_log WHERE actor = 'rules-only' AND action = 'search_clauses' AND result = 'DENIED'",
				Integer.class)).isPositive();
	}

	@Test
	void listsAndCallsTheFourReadOnlyTools() {
		try (McpSyncClient client = client("k-full-000000001")) {
			List<String> tools = client.listTools().tools().stream().map(McpSchema.Tool::name).sorted().toList();
			assertThat(tools).containsExactly("check_algorithm", "compute_score", "get_clause", "search_clauses");

			McpSchema.CallToolResult search = client
				.callTool(new McpSchema.CallToolRequest("search_clauses", Map.of("query", "密钥 生命周期", "k", 3), null));
			assertThat(search.isError()).isNotEqualTo(Boolean.TRUE);
			assertThat(text(search)).contains("FIX/T 0001-2026#6.3.1");

			McpSchema.CallToolResult clause = client
				.callTool(new McpSchema.CallToolRequest("get_clause", Map.of("clause_ref", "FIX/T 0001-2026#5.2.3"), null));
			assertThat(text(clause)).contains("重要数据在网络中传输时应加密");

			McpSchema.CallToolResult algorithm = client
				.callTool(new McpSchema.CallToolRequest("check_algorithm", Map.of("name", "sm4_gcm"), null));
			assertThat(text(algorithm)).contains("\"name\":\"SM4\"").contains("ruleVersion");

			// 应用和数据 5.4.3：D√ A× K√ Ra=0.5 → 0.25；管理制度 6.1.1 符合 → 70×0.25 + 30×1 = 47.50
			McpSchema.CallToolResult score = client.callTool(new McpSchema.CallToolRequest("compute_score",
					Map.of("level", 3, "findings",
							List.of(Map.of("object", "db", "layer", "应用和数据", "clause_ref", "FIX/T 0001-2026#5.4.3", "d", true,
									"a", false, "k", true, "ra", 0.5),
									Map.of("object", "org", "layer", "管理制度", "clause_ref", "FIX/T 0001-2026#6.1.1",
											"judgment", "符合"))),
					null));
			assertThat(text(score)).contains("\"total\":47.50").contains("scoring.v2-test");

			McpSchema.CallToolResult invalid = client
				.callTool(new McpSchema.CallToolRequest("search_clauses", Map.of("query", "密钥", "k", 50), null));
			assertThat(invalid.isError()).isTrue();
		}
		List<Map<String, Object>> audit = jdbcTemplate
			.queryForList("SELECT action, result, args_sha256 FROM audit_log WHERE actor = 'full' ORDER BY id");
		assertThat(audit).extracting(r -> r.get("action"))
			.contains("search_clauses", "get_clause", "check_algorithm", "compute_score");
		assertThat(audit).allSatisfy(r -> assertThat((String) r.get("args_sha256")).hasSize(64));
		assertThat(audit).anySatisfy(r -> assertThat(r.get("result")).isEqualTo("ERROR"));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target LIKE '%密钥%'", Integer.class))
			.isZero();
	}

	@Test
	void otherApisStayOpenButAdminApisNeedAdminScope() throws Exception {
		HttpClient http = HttpClient.newHttpClient();
		HttpResponse<String> health = http.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/kb/documents")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(health.statusCode()).isEqualTo(200);
		HttpResponse<String> reindex = http.send(HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/kb/reindex"))
			.header("X-API-Key", "k-full-000000001")
			.POST(HttpRequest.BodyPublishers.noBody())
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(reindex.statusCode()).isEqualTo(403);
	}

}
