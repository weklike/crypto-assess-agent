package com.cryptoassess.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cryptoassess.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void initialMigrationCreatesCoreTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);

		assertThat(tables).contains("kb_document", "kb_clause", "llm_call", "audit_log");
	}

	@Test
	void clauseRefIsUnique() {
		Integer unique = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM information_schema.statistics
				WHERE table_schema = DATABASE() AND table_name = 'kb_clause'
				  AND column_name = 'clause_ref' AND non_unique = 0""", Integer.class);

		assertThat(unique).isEqualTo(1);
	}

}
