package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeChatModel;
import com.cryptoassess.support.FakeRerankConfiguration;
import com.cryptoassess.support.JudgeOutputs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = { "app.knowledge.normalized-dir=data/fixtures", "app.workflow.resume-on-startup=false",
		"eval.dataset-path=data/fixtures/systems", "eval.dataset=v1" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class GapEvalIT {

	static final Path resultsDir = TempDirs.create("gap-eval");

	private static final Pattern TARGET = Pattern.compile("测评指标条款：\\s*\\[([^\\]]+)\\]");

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
	void comparesPlainPromptWithWorkflow() throws Exception {
		chatModel.reset();
		chatModel.setResponder(prompt -> {
			if (prompt.contains("一次性找出")) {
				return FakeChatModel.reply("""
						{"gaps":[{"object":"db","clause_ref":"FIX/T 0001-2026#5.4.3","judgment":"不符合","reason":"AES"},
						         {"object":"vpn","clause_ref":"FIX/T 0001-2026#5.2.1","judgment":"不符合","reason":"猜测"}]}""");
			}
			Matcher m = TARGET.matcher(prompt);
			String ref = m.find() ? m.group(1) : "none";
			String judgment = switch (ref) {
				case "FIX/T 0001-2026#5.4.3" -> "不符合";
				case "FIX/T 0001-2026#5.2.5" -> "部分符合";
				default -> "符合";
			};
			return FakeChatModel.reply(JudgeOutputs.of(judgment, ref));
		});

		EvalRunResult result = evalService.run("gap");

		assertThat(result.completed()).isTrue();
		Map<String, Object> plain = (Map<String, Object>) result.outcome().metrics().get("plain_prompt");
		Map<String, Object> workflow = (Map<String, Object>) result.outcome().metrics().get("workflow");
		assertThat((Double) plain.get("recall")).isEqualTo(0.5);
		assertThat((Double) plain.get("falsePositiveRate")).isEqualTo(0.5);
		assertThat((Double) workflow.get("recall")).isEqualTo(1.0);
		assertThat((Double) workflow.get("falsePositiveRate")).isEqualTo(0.0);
		// 纯提示词 1 次调用；工作流 7 + 5 条指标各 1 次
		assertThat(chatModel.calls()).isEqualTo(13);
		String report = Files.readString(result.outputDir().resolve("report.md"));
		assertThat(report).contains("纯提示词", "工作流", "召回率", "误报率", "fixture-01", "待复核", "FIX/T 0001-2026#5.2.1");
		assertThat(Files.readAllLines(result.outputDir().resolve("cases.jsonl"))).hasSize(2);
	}

}
