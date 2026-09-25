package com.cryptoassess.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.format.NormalizedFormatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KnowledgeIngestIT {

	@TempDir
	static Path normalizedDir;

	@Autowired
	private KnowledgeIngestService ingestService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void normalizedDir(DynamicPropertyRegistry registry) {
		registry.add("app.knowledge.normalized-dir", () -> normalizedDir.toString());
	}

	@BeforeEach
	void cleanTables() throws IOException {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		Files.copy(Path.of("data/fixtures/mock-standard.md"), normalizedDir.resolve("mock-standard.md"),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	@Test
	void importIsIdempotentOnNormalizedContent() {
		ImportResult first = ingestService.importDocument("mock-standard.md");
		ImportResult second = ingestService.importDocument("mock-standard.md");

		assertThat(first.created()).isTrue();
		assertThat(second.created()).isFalse();
		assertThat(second.documentId()).isEqualTo(first.documentId());
		assertThat(first.clauseCount()).isEqualTo(36);
		assertThat(count("kb_clause")).isEqualTo(36);
		assertThat(count("kb_document")).isEqualTo(1);
	}

	@Test
	void clauseCanBeLookedUpByRef() {
		ingestService.importDocument("mock-standard.md");

		KbClause clause = ingestService.findClause("FIX/T 0001-2026#5.2.3").orElseThrow();

		assertThat(clause.title()).isEqualTo("通信过程中重要数据的机密性");
		assertThat(clause.layer()).isEqualTo("网络和通信");
		assertThat(clause.levels()).isEqualTo("1,2,3,4");
		assertThat(clause.path()).isEqualTo("5 技术要求 > 5.2 网络和通信");
		assertThat(ingestService.findClause("FIX/T 0001-2026#9.9")).isEmpty();
	}

	@Test
	void clausesCanBeBrowsedPageByPageWithFilters() {
		ingestService.importDocument("mock-standard.md");

		KnowledgeIngestService.ClausePage first = ingestService.clausePage(1, 10, null, null);
		KnowledgeIngestService.ClausePage last = ingestService.clausePage(4, 10, null, null);

		assertThat(first.total()).isEqualTo(36);
		assertThat(first.items()).hasSize(10).extracting(KbClause::clauseRef).first().isEqualTo("FIX/T 0001-2026#4");
		assertThat(last.items()).hasSize(6);
		KnowledgeIngestService.ClausePage physical = ingestService.clausePage(1, 10, "物理和环境", 1);
		assertThat(physical.items()).extracting(KbClause::clauseRef).containsExactly("FIX/T 0001-2026#5.1.1");
		assertThat(physical.total()).isEqualTo(1);
		assertThatThrownBy(() -> ingestService.clausePage(0, 10, null, null)).hasMessageContaining("page");
		assertThatThrownBy(() -> ingestService.clausePage(1, 51, null, null)).hasMessageContaining("size");
	}

	@Test
	void changedContentSupersedesPreviousVersion() throws IOException {
		ImportResult first = ingestService.importDocument("mock-standard.md");
		Path file = normalizedDir.resolve("mock-standard.md");
		Files.writeString(file, Files.readString(file).replace("本仿标准中的密码保护", "修订后的密码保护"));

		ImportResult second = ingestService.importDocument("mock-standard.md");

		assertThat(second.created()).isTrue();
		assertThat(second.documentId()).isNotEqualTo(first.documentId());
		assertThat(count("kb_clause")).isEqualTo(36);
		assertThat(ingestService.listDocuments()).extracting(KbDocument::status)
			.containsExactlyInAnyOrder("ACTIVE", "SUPERSEDED");
		assertThat(ingestService.findClause("FIX/T 0001-2026#4.1").orElseThrow().body()).startsWith("修订后的");
	}

	@Test
	void invalidFileIsRejectedWithoutPartialImport() throws IOException {
		Files.writeString(normalizedDir.resolve("broken.md"), """
				---
				doc_code: FIX/T 0002-2026
				title: t
				source_sha256: %s
				normalized_by: x
				---
				# 5 章
				正文。
				## 5.2 跳号
				正文。
				""".formatted("0".repeat(64)));

		assertThatThrownBy(() -> ingestService.importDocument("broken.md"))
			.isInstanceOf(NormalizedFormatException.class);
		assertThat(count("kb_document")).isZero();
		assertThat(count("kb_clause")).isZero();
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
