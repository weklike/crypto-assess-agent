package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeChatModel;
import com.cryptoassess.support.FakeRerankConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = { "app.knowledge.normalized-dir=data/fixtures",
		"eval.dataset-path=data/fixtures/qa_bank.fixture.jsonl", "eval.dataset=fixture" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class QaEvalIT {

	static final Path resultsDir = TempDirs.create("qa-eval");

	@Autowired
	private EvalService evalService;

	@Autowired
	private FakeChatModel chatModel;

	@DynamicPropertySource
	static void results(DynamicPropertyRegistry registry) {
		registry.add("eval.results-dir", () -> resultsDir.toString());
	}

	@BeforeAll
	void indexFixture(@Autowired JdbcTemplate jdbcTemplate, @Autowired KnowledgeIngestService ingestService,
			@Autowired ClauseIndexer indexer) {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		ingestService.importDocument("mock-standard.md");
		indexer.reindex();
	}

	@Test
	@SuppressWarnings("unchecked")
	void runsThreeGroupsAndWritesOnlyIdsAndCorrectness() throws Exception {
		chatModel.reset();
		// 第一次调用输出不合规，其余都回答 A 并引用一个检索结果里多半没有的条款
		chatModel.enqueue(FakeChatModel.reply("我觉得选 A"));
		chatModel.setFallback(FakeChatModel.reply("{\"answer\":[\"A\"],\"citations\":[\"FIX/T 0001-2026#5.4.3\"]}"));

		EvalRunResult result = evalService.run("qa");

		assertThat(result.completed()).isTrue();
		List<String> cases = Files.readAllLines(result.outputDir().resolve("cases.jsonl"));
		assertThat(cases).hasSize(6 * 3);
		assertThat(cases).allSatisfy(line -> assertThat(line).matches(
				"\\{\"id\":\"q\\d{4}\",\"group\":\"(none|hybrid|hybrid_rerank)\",\"correct\":(true|false)\\}"));
		String report = Files.readString(result.outputDir().resolve("report.md"));
		assertThat(report).contains("三组对比", "拒答阈值扫描", "人工核对", "qa-exam.v1", "fake-local-exam")
			.doesNotContain("仿标准中");
		// 题库只发往本地模型：运行信息里写明端点
		assertThat(result.outcome().config()).containsEntry("examModelEndpoint", "fake://local");
		Map<String, Object> none = (Map<String, Object>) result.outcome().metrics().get("none");
		assertThat((Map<String, Long>) none.get("statusCounts")).containsEntry("invalid_output", 1L);
		// 每题三组，共 18 次模型调用，无重试
		assertThat(chatModel.calls()).isEqualTo(18);
		Map<String, Object> hybrid = (Map<String, Object>) result.outcome().metrics().get("hybrid");
		assertThat((Long) hybrid.get("citationsTotal")).isEqualTo(6L);
	}

}
