package com.cryptoassess.common.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class MysqlCheck implements DependencyCheck {

	private final JdbcTemplate jdbcTemplate;

	MysqlCheck(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public String name() {
		return "mysql";
	}

	@Override
	public CheckResult check() {
		String version = this.jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
		return CheckResult.up("version " + version);
	}

}
