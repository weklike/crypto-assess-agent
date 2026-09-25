package com.cryptoassess.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import com.cryptoassess.support.ElasticsearchIkImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IkAnalyzerIT {

	@Container
	static final ElasticsearchContainer ES = ElasticsearchIkImage.newContainer();

	static ElasticsearchClient client;

	@BeforeAll
	static void createClient() {
		client = ElasticsearchClient
			.of(b -> b.host("http://" + ES.getHttpHostAddress()).jsonMapper(new Jackson3JsonpMapper()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "密评", "商用密码", "测评对象", "密钥管理", "身份鉴别", "商用密码应用安全性评估" })
	void customDictionaryTermIsKeptAsOneToken(String term) throws IOException {
		assertThat(tokens("ik_smart", term)).containsExactly(term);
		assertThat(tokens("ik_max_word", term)).contains(term);
	}

	@Test
	void customTermsSurviveInsideSentence() throws IOException {
		assertThat(tokens("ik_smart", "密评时核查测评对象的商用密码使用情况")).contains("密评", "测评对象", "商用密码");
	}

	@Test
	void ikMaxWordEmitsFinerGrainedTokensThanIkSmart() throws IOException {
		List<String> smart = tokens("ik_smart", "商用密码应用安全性评估");
		List<String> maxWord = tokens("ik_max_word", "商用密码应用安全性评估");

		assertThat(smart).containsExactly("商用密码应用安全性评估");
		assertThat(maxWord).contains("商用密码", "密码应用", "评估").hasSizeGreaterThan(smart.size());
	}

	@Test
	void elasticsearchCheckReportsIkAvailable() {
		CheckResult result = new ElasticsearchCheck(client).check();

		assertThat(result.status()).isEqualTo(CheckResult.Status.UP);
		assertThat(result.detail()).contains("ik");
	}

	private static List<String> tokens(String analyzer, String text) throws IOException {
		return client.indices()
			.analyze(a -> a.analyzer(analyzer).text(text))
			.tokens()
			.stream()
			.map(AnalyzeToken::token)
			.toList();
	}

}
