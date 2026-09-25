package com.cryptoassess.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
class RulesConfiguration {

	private static final Logger log = LoggerFactory.getLogger(RulesConfiguration.class);

	/** 启动时加载并校验；YAML 不合法时抛 RuleTableException，应用启动失败。 */
	@Bean
	AlgorithmRuleTable algorithmRuleTable(@Value("${app.rules.algorithms}") Resource resource) {
		AlgorithmRuleTable table = AlgorithmRuleTable.load(resource);
		if (!table.reviewed()) {
			log.warn("Algorithm rule table {} is a draft (reviewed: false); conclusions must be confirmed",
					table.ruleVersion());
		}
		return table;
	}

}
