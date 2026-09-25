package com.cryptoassess.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
class ScoringConfiguration {

	private static final Logger log = LoggerFactory.getLogger(ScoringConfiguration.class);

	/** 按《量化评估规则（2023 版）》组织的评分规则；结构不合法时启动失败。 */
	@Bean
	OfficialScoringRules officialScoringRules(@Value("${app.rules.scoring}") Resource resource) {
		OfficialScoringRules rules = OfficialScoringRules.load(resource);
		if (!rules.reviewed()) {
			log.warn("Scoring rules {} are not reviewed yet (reviewed: false); scores must be confirmed", rules.version());
		}
		return rules;
	}

}
