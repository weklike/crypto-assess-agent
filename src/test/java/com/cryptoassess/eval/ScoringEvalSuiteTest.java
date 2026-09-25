package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.cryptoassess.eval.scoring.ScoringEvalSuite;
import com.cryptoassess.rules.OfficialScoringRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class ScoringEvalSuiteTest {

	private static final OfficialScoringRules RULES = OfficialScoringRules
		.load(new ClassPathResource("rules/scoring.v2.test.yaml"));

	private static final OfficialScoringRules PRODUCTION = OfficialScoringRules
		.load(new ClassPathResource("rules/scoring.v2.yaml"));

	@TempDir
	Path out;

	@Test
	void productionRulesAgreeWithResearchCandidatesGolden() throws Exception {
		// 期望值来自调研算术候选（S08），不是由本项目计算器生成
		EvalContext context = new EvalContext("scoring", "v2", Path.of("src/test/resources/scoring_golden.v2.yaml"), out,
				"abc123", Instant.now());

		EvalOutcome outcome = new ScoringEvalSuite(PRODUCTION).run(context);

		assertThat((java.util.List<?>) outcome.metrics().get("mismatches")).isEmpty();
		assertThat(outcome.metrics()).containsEntry("cases", 33).containsEntry("agreed", 33);
	}

	@Test
	void goldenCasesAgreeWithCalculator() throws Exception {
		EvalContext context = new EvalContext("scoring", "v2-test",
				Path.of("src/test/resources/scoring_golden.v2-test.yaml"), out, "abc123", Instant.now());

		EvalOutcome outcome = new ScoringEvalSuite(RULES).run(context);

		assertThat((java.util.List<?>) outcome.metrics().get("mismatches")).isEmpty();
		assertThat(outcome.metrics()).containsEntry("cases", 16).containsEntry("agreed", 16).containsEntry("agreement", 1.0);
		assertThat(Files.readString(out.resolve("report.md"))).contains("16 / 16", "scoring.v2-test");
		assertThat(Files.readAllLines(out.resolve("cases.jsonl"))).hasSize(16);
	}

	@Test
	void mismatchAndUnexpectedErrorAreReportedNotHidden() throws Exception {
		Path golden = out.resolve("golden.yaml");
		Files.writeString(golden, """
				cases:
				  - id: bad
				    level: 3
				    findings:
				      - {object: o1, layer: 应用和数据, clause_ref: "FIX/T 0001-2026#5.4.3", d: true, a: true, k: true}
				      - {object: o2, layer: 管理制度, clause_ref: "FIX/T 0001-2026#6.1.1", judgment: 符合}
				    total: "99.00"
				  - id: missing-dims
				    level: 3
				    findings:
				      - {object: o1, layer: 应用和数据, clause_ref: "FIX/T 0001-2026#5.4.3", d: true}
				    total: "0.00"
				""");

		EvalOutcome outcome = new ScoringEvalSuite(RULES)
			.run(new EvalContext("scoring", "x", golden, out, "abc", Instant.now()));

		assertThat(outcome.metrics()).containsEntry("agreed", 0);
		assertThat((java.util.List<?>) outcome.metrics().get("mismatches")).hasSize(2);
		assertThat(Files.readString(out.resolve("report.md"))).contains("bad", "99.00", "100.00", "计算报错");
	}

}
