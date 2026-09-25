package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cryptoassess.eval.summary.SummaryEvalSuite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SummaryEvalSuiteTest {

	@TempDir
	Path results;

	@Test
	void runsEverySuiteOnSameVersionAndLinksSubReports() throws Exception {
		Path summaryDir = Files.createDirectory(results.resolve("20260101T000000Z-summary"));
		SummaryEvalSuite.Runner runner = suite -> {
			Path dir = Files.createDirectory(results.resolve("20260101T000001Z-" + suite));
			if (suite.equals("qa")) {
				return new EvalRunResult(suite, dir, false, null, "qa_bank_sample.v1.jsonl not found");
			}
			return new EvalRunResult(suite, dir, true,
					new EvalOutcome(Map.of("gitCommit", "abc123"), Map.of("headline", 0.5)), null);
		};

		EvalOutcome outcome = new SummaryEvalSuite(runner, List.of("retrieval", "qa", "gap", "scoring"))
			.run(new EvalContext("summary", "v1", null, summaryDir, "abc123", Instant.now()));

		String report = Files.readString(summaryDir.resolve("report.md"));
		assertThat(report).contains("../20260101T000001Z-retrieval/report.md", "../20260101T000001Z-gap/report.md")
			.contains("qa", "失败", "qa_bank_sample.v1.jsonl not found")
			.contains("abc123");
		assertThat(outcome.metrics()).containsEntry("completed", 3).containsEntry("failed", 1);
	}

}
