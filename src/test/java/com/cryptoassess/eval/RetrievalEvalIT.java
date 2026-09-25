package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
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
		"eval.dataset-path=data/fixtures/retrieval_queries.fixture.jsonl", "eval.dataset=fixture" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class RetrievalEvalIT {

	// Spring 上下文在 JUnit 注入静态 @TempDir 之前加载，这里自己建临时目录
	static final Path resultsDir = createTempDir();

	private static Path createTempDir() {
		try {
			return Files.createTempDirectory("eval-results");
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	@Autowired
	private EvalService evalService;

	@Autowired
	private List<EvalSuite> suites;

	@Autowired
	private EvalRunMapper evalRunMapper;

	@Autowired
	private EvalProperties properties;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void results(DynamicPropertyRegistry registry) {
		registry.add("eval.results-dir", () -> resultsDir.toString());
	}

	@BeforeAll
	void indexFixture(@Autowired KnowledgeIngestService ingestService, @Autowired ClauseIndexer indexer) {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		ingestService.importDocument("mock-standard.md");
		indexer.reindex();
	}

	@Test
	void producesReportMetricsAndCasesAndRecordsRun() throws Exception {
		EvalRunResult result = evalService.run("retrieval");

		assertThat(result.completed()).isTrue();
		assertThat(result.outputDir().getFileName().toString()).matches("\\d{8}T\\d{6}Z-retrieval");
		assertThat(Files.readAllLines(result.outputDir().resolve("cases.jsonl"))).hasSize(15 * 4);
		String report = Files.readString(result.outputDir().resolve("report.md"));
		assertThat(report).contains("# 检索评测报告", "gitCommit", "datasetVersion", "fixture", "embeddingModel",
				"rerankModel", "kb_clause_v1_", "hybrid_rerank", "按问法分组", "按安全层面分组", "最差 10 条");
		assertThat(Files.readString(result.outputDir().resolve("metrics.json"))).contains("\"recall@5\"", "\"mrr@10\"",
				"\"ndcg@10\"", "\"p95Ms\"");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM eval_run WHERE suite = 'retrieval' AND status = 'COMPLETED' AND output_dir = ?",
				Integer.class, result.outputDir().toString())).isEqualTo(1);
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> bm25 = (java.util.Map<String, Object>) result.outcome().metrics().get("bm25");
		assertThat((Double) bm25.get("recall@10")).isGreaterThan(0.5);
	}

	@Test
	void refusesToOverwriteExistingResultDirectory() throws Exception {
		Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		EvalService service = new EvalService(suites, evalRunMapper, properties, fixed);
		Files.createDirectories(resultsDir.resolve("20260101T000000Z-retrieval"));

		assertThatThrownBy(() -> service.run("retrieval")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("already exists");
	}

	@Test
	void invalidDatasetFailsButKeepsTheRunDirectory() throws Exception {
		Path bad = resultsDir.resolve("bad.jsonl");
		Files.writeString(bad, "{\"id\":\"x\",\"query\":\"q\",\"gold_refs\":[\"NOPE#1\"],\"style\":\"keyword\"}\n");
		EvalProperties badProps = new EvalProperties("retrieval", null, "bad", bad.toString(), resultsDir.toString(),
				"eval/datasets");
		EvalService service = new EvalService(suites, evalRunMapper, badProps,
				Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));

		EvalRunResult result = service.run("retrieval");

		assertThat(result.completed()).isFalse();
		assertThat(result.exitCode()).isEqualTo(1);
		assertThat(Files.readString(result.outputDir().resolve("error.txt"))).contains("NOPE#1");
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM eval_run WHERE output_dir = ?", String.class,
				result.outputDir().toString())).isEqualTo("FAILED");
	}

}
