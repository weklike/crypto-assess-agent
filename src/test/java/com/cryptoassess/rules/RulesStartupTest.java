package com.cryptoassess.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RulesStartupTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(RulesConfiguration.class);

	@Test
	void invalidRuleTableStopsStartupWithReason() {
		runner.withPropertyValues("app.rules.algorithms=classpath:rules/algorithms.invalid.yaml").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(RuleTableException.class)
				.hasMessageContaining("algorithms[0]")
				.hasMessageContaining("PERHAPS");
		});
	}

	@Test
	void validRuleTableStarts() {
		runner.withPropertyValues("app.rules.algorithms=classpath:rules/algorithms.test.yaml")
			.run(context -> assertThat(context).hasSingleBean(AlgorithmRuleTable.class));
	}

}
