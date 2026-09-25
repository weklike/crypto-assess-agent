package com.cryptoassess.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeRerankClient;
import com.cryptoassess.support.FakeRerankConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.knowledge.normalized-dir=data/fixtures")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class SearchIT {

	@Autowired
	private HybridSearchService searchService;

	@Autowired
	private FakeRerankClient rerankClient;

	@Autowired
	private MockMvc mockMvc;

	@BeforeAll
	void indexFixture(@Autowired JdbcTemplate jdbcTemplate, @Autowired KnowledgeIngestService ingestService,
			@Autowired ClauseIndexer indexer) {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		ingestService.importDocument("mock-standard.md");
		indexer.reindex();
	}

	@AfterEach
	void restoreRerank() {
		rerankClient.setAvailable(true);
	}

	@ParameterizedTest
	@EnumSource(SearchMode.class)
	void everyModeFindsTheObviousClause(SearchMode mode) {
		SearchResponse response = searchService.search(new SearchRequest("门禁记录防篡改", mode, 5, null, null));

		assertThat(response.hits()).isNotEmpty();
		assertThat(response.hits()).extracting(SearchHit::clauseRef).contains("FIX/T 0001-2026#5.1.2");
		assertThat(response.timings()).containsKey("totalMs");
	}

	@ParameterizedTest
	@EnumSource(SearchMode.class)
	void layerFilterAppliesToEveryMode(SearchMode mode) {
		SearchResponse response = searchService.search(new SearchRequest("身份鉴别", mode, 10, "网络和通信", null));

		assertThat(response.hits()).isNotEmpty().allSatisfy(hit -> assertThat(hit.layer()).isEqualTo("网络和通信"));
	}

	@ParameterizedTest
	@EnumSource(SearchMode.class)
	void levelFilterExcludesClausesNotApplicableToThatLevel(SearchMode mode) {
		List<String> refs = searchService.search(new SearchRequest("视频监控录像完整性", mode, 36, null, 1))
			.hits()
			.stream()
			.map(SearchHit::clauseRef)
			.toList();

		// 5.1.3 只适用于第三、四级
		assertThat(refs).doesNotContain("FIX/T 0001-2026#5.1.3");
	}

	@Test
	void hybridHitsCarryBothRanks() {
		SearchHit top = searchService.search(new SearchRequest("通信数据完整性", SearchMode.HYBRID, 3, null, null))
			.hits()
			.get(0);

		assertThat(top.rrfScore()).isNotNull();
		assertThat(top.bm25Rank()).isNotNull();
		assertThat(top.denseRank()).isNotNull();
	}

	@Test
	void rerankDownGives503ProblemDetail() throws Exception {
		rerankClient.setAvailable(false);

		mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
			.content("{\"query\":\"身份鉴别\",\"mode\":\"hybrid_rerank\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.type").value("urn:crypto-assess:error:dependency-unavailable"));
	}

	@Test
	void searchEndpointReturnsHitsAndTimings() throws Exception {
		mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
			.content("{\"query\":\"数据库敏感信息加密存储\",\"mode\":\"hybrid\",\"k\":3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("hybrid"))
			.andExpect(jsonPath("$.hits.length()").value(3))
			.andExpect(jsonPath("$.timings.totalMs").exists());
	}

	@Test
	void invalidRequestIs400() throws Exception {
		mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"\",\"k\":99}"))
			.andExpect(status().isBadRequest());
	}

}
