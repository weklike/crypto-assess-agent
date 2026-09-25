package com.cryptoassess.common.llm;

import java.time.Duration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 构造每次模型请求的选项。两个 Spring AI 2.0.1 的坑在这里集中处理：
 * 1. OpenAiChatModel 会把提示词自带的选项原样强转成 OpenAiChatOptions，所以必须从模型自己的默认选项出发修改；
 * 2. OpenAiChatOptions 自带每次请求的超时，默认 60 秒且无法通过配置修改（toOptions 不复制 timeout），
 * 它会覆盖客户端的 spring.ai.openai.timeout，所以这里显式设置为同一个值。
 */
@Component
public class LlmRequestOptions {

	private final Duration requestTimeout;

	public LlmRequestOptions(@Value("${spring.ai.openai.timeout:540s}") Duration requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public ChatOptions.Builder<?> builder(ChatModel model, Double temperature) {
		ChatOptions.Builder<?> builder = model.getOptions().mutate();
		if (builder instanceof OpenAiChatOptions.Builder openAi) {
			openAi.timeout(this.requestTimeout);
		}
		if (temperature != null) {
			builder.temperature(temperature);
		}
		return builder;
	}

	public Duration requestTimeout() {
		return this.requestTimeout;
	}

}
