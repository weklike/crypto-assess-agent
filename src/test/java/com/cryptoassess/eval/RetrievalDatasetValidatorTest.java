package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.cryptoassess.eval.dataset.DatasetIssue;
import com.cryptoassess.eval.dataset.RetrievalDatasetValidator;
import com.cryptoassess.eval.dataset.RetrievalDatasetValidator.Report;
import org.junit.jupiter.api.Test;

class RetrievalDatasetValidatorTest {

	private static final Set<String> KNOWN = Set.of("FIX/T 0001-2026#5.2.3", "FIX/T 0001-2026#5.4.3",
			"FIX/T 0001-2026#6.3.1");

	private final RetrievalDatasetValidator validator = new RetrievalDatasetValidator(KNOWN::contains);

	@Test
	void validDatasetPassesAndReportsStyleCounts() {
		Report report = validator.validate(List.of(
				"{\"id\":\"r001\",\"query\":\"数据库里的身份证号要不要加密\",\"gold_refs\":[\"FIX/T 0001-2026#5.4.3\"],\"style\":\"colloquial\",\"layer\":\"应用和数据\",\"level\":3,\"note\":\"\"}",
				"{\"id\":\"r002\",\"query\":\"传输重要数据时的机密性保护\",\"gold_refs\":[\"FIX/T 0001-2026#5.2.3\"],\"style\":\"paraphrase\",\"layer\":null,\"level\":null}",
				"{\"id\":\"r003\",\"query\":\"密钥 生命周期\",\"gold_refs\":[\"FIX/T 0001-2026#6.3.1\"],\"style\":\"keyword\"}"));

		assertThat(report.issues()).isEmpty();
		assertThat(report.queries()).hasSize(3);
		assertThat(report.styleCounts()).containsEntry("colloquial", 1L)
			.containsEntry("paraphrase", 1L)
			.containsEntry("keyword", 1L);
	}

	@Test
	void reportsLineLevelProblems() {
		Report report = validator.validate(List.of(
				"{\"id\":\"r001\",\"query\":\"q\",\"gold_refs\":[\"FIX/T 0001-2026#5.4.3\"],\"style\":\"colloquial\"}",
				"{\"id\":\"r001\",\"query\":\"q2\",\"gold_refs\":[\"FIX/T 0001-2026#9.9\"],\"style\":\"keyword\"}",
				"{\"id\":\"r003\",\"query\":\"\",\"gold_refs\":[],\"style\":\"slang\",\"level\":5,\"layer\":\"应用安全\"}",
				"not json"));

		assertThat(report.issues()).extracting(DatasetIssue::line, DatasetIssue::message)
			.anySatisfy(t -> assertThat(t.toList()).containsExactly(2, "duplicate id r001"))
			.anySatisfy(t -> assertThat(t.toList()).containsExactly(2, "gold_ref not in kb_clause: FIX/T 0001-2026#9.9"));
		assertThat(report.issues()).filteredOn(i -> i.line() == 3)
			.extracting(DatasetIssue::message)
			.anyMatch(m -> m.contains("query"))
			.anyMatch(m -> m.contains("gold_refs"))
			.anyMatch(m -> m.contains("style"))
			.anyMatch(m -> m.contains("level"))
			.anyMatch(m -> m.contains("layer"));
		assertThat(report.issues()).anyMatch(i -> i.line() == 4 && i.message().contains("JSON"));
	}

	@Test
	void missingStyleIsReportedForTheWholeDataset() {
		Report report = validator.validate(List.of(
				"{\"id\":\"r001\",\"query\":\"q\",\"gold_refs\":[\"FIX/T 0001-2026#5.4.3\"],\"style\":\"colloquial\"}"));

		assertThat(report.issues()).anyMatch(i -> i.line() == 0 && i.message().contains("paraphrase"))
			.anyMatch(i -> i.line() == 0 && i.message().contains("keyword"));
	}

}
