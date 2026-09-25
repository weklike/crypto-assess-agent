package com.cryptoassess.common.health;

import java.io.IOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import org.springframework.stereotype.Component;

/**
 * 除了集群状态，还用 ik_smart 分析一段中文：插件没装好时要么报错，要么退化成逐字切分。
 */
@Component
class ElasticsearchCheck implements DependencyCheck {

	private static final String SAMPLE = "商用密码应用安全性评估";

	private final ElasticsearchClient client;

	ElasticsearchCheck(ElasticsearchClient client) {
		this.client = client;
	}

	@Override
	public String name() {
		return "elasticsearch";
	}

	@Override
	public CheckResult check() {
		try {
			HealthResponse health = this.client.cluster().health();
			String clusterStatus = health.status().jsonValue();
			if ("red".equals(clusterStatus)) {
				return CheckResult.down("cluster status red");
			}
			List<String> tokens = this.client.indices()
				.analyze(a -> a.analyzer("ik_smart").text(SAMPLE))
				.tokens()
				.stream()
				.map(AnalyzeToken::token)
				.toList();
			if (!isWordSegmented(tokens)) {
				return CheckResult.down("ik_smart splits text into single characters: " + tokens);
			}
			return CheckResult.up("cluster " + clusterStatus + ", ik_smart tokens " + tokens);
		}
		catch (IOException ex) {
			return CheckResult.down(ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

	static boolean isWordSegmented(List<String> tokens) {
		return tokens.stream().anyMatch(token -> token.codePointCount(0, token.length()) > 1);
	}

}
