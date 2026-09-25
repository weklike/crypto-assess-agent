package com.cryptoassess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationContextIT {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsAgainstMysqlContainer() {
		String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);

		assertThat(version).startsWith("8.4");
	}

	@Test
	void modelHttpTimeoutIsAboveBusinessTimeouts() {
		OpenAiChatModel model = context.getBean(OpenAiChatModel.class);
		OpenAiChatOptions options = (OpenAiChatOptions) context.getBean(com.cryptoassess.common.llm.LlmRequestOptions.class)
			.builder(model, 0.0)
			.build();

		// 每次请求的超时（OpenAiChatOptions 默认 60 s）必须大于判定超时 90 s，否则 SDK 会先超时
		assertThat(options.getTimeout()).isEqualTo(java.time.Duration.ofSeconds(540));
		assertThat(options.getModel()).isEqualTo(((OpenAiChatOptions) model.getOptions()).getModel());
		assertThat(options.getTemperature()).isEqualTo(0.0);
	}

	@Test
	void usesOpenAiForChatAndOllamaForEmbedding() {
		assertThat(context.getBeansOfType(ChatModel.class).values())
			.singleElement()
			.isInstanceOf(OpenAiChatModel.class);
		assertThat(context.getBeansOfType(EmbeddingModel.class).values())
			.singleElement()
			.isInstanceOf(OllamaEmbeddingModel.class);
	}

}
