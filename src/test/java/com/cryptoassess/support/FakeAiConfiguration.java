package com.cryptoassess.support;

import com.cryptoassess.eval.qabank.ExamChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 用假模型替换真实的 Ollama embedding、OpenAI 兼容对话模型和题库评测用的本地模型，集成测试不访问任何模型服务。
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAiConfiguration {

	@Bean
	@Primary
	FakeEmbeddingModel fakeEmbeddingModel() {
		return new FakeEmbeddingModel();
	}

	@Bean
	@Primary
	FakeChatModel fakeChatModel() {
		return new FakeChatModel();
	}

	@Bean
	@Primary
	ExamChatModel fakeExamChatModel(FakeChatModel fakeChatModel) {
		return new ExamChatModel() {

			@Override
			public ChatModel chatModel() {
				return fakeChatModel;
			}

			@Override
			public String modelName() {
				return "fake-local-exam";
			}

			@Override
			public String endpoint() {
				return "fake://local";
			}

		};
	}

}
