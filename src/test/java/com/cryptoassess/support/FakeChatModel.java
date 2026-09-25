package com.cryptoassess.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * 按脚本返回的假对话模型：正常分块输出、挂起（模拟超时）、空回复、抛异常。
 * 脚本按调用顺序消费；脚本用完后重复最后一个默认脚本。
 */
public class FakeChatModel implements ChatModel {

	public static final String MODEL = "fake-chat";

	public record Script(List<String> chunks, Duration hang, RuntimeException error) {
	}

	public static Script reply(String... chunks) {
		return new Script(List.of(chunks), null, null);
	}

	public static Script hang(Duration duration) {
		return new Script(List.of(), duration, null);
	}

	public static Script empty() {
		return new Script(List.of(), null, null);
	}

	public static Script fail(RuntimeException error) {
		return new Script(List.of(), null, error);
	}

	private final Deque<Script> scripts = new ConcurrentLinkedDeque<>();

	private final List<Prompt> prompts = Collections.synchronizedList(new ArrayList<>());

	private volatile Script fallback = reply("默认回答。");

	/** 按提示词生成回复；设置后优先于脚本队列之外的默认回复。 */
	private volatile java.util.function.Function<String, Script> responder;

	public void setResponder(java.util.function.Function<String, Script> responder) {
		this.responder = responder;
	}

	public FakeChatModel enqueue(Script... next) {
		this.scripts.addAll(List.of(next));
		return this;
	}

	public void setFallback(Script script) {
		this.fallback = script;
	}

	public void reset() {
		this.scripts.clear();
		this.prompts.clear();
		this.fallback = reply("默认回答。");
		this.responder = null;
	}

	public List<Prompt> prompts() {
		return List.copyOf(this.prompts);
	}

	public int calls() {
		return this.prompts.size();
	}

	/** 与 OpenAiChatModel 一致：默认选项是 OpenAiChatOptions。 */
	@Override
	public ChatOptions getOptions() {
		return OpenAiChatOptions.builder().model(MODEL).build();
	}

	/**
	 * 与 OpenAiChatModel 一致：提示词带了选项时原样使用并强转成 OpenAiChatOptions，
	 * 传入通用 ChatOptions 会抛 ClassCastException（真实模型上踩过的坑，测试要能复现）。
	 */
	private static void requireModelOptions(Prompt prompt) {
		if (prompt.getOptions() != null && !(prompt.getOptions() instanceof OpenAiChatOptions)) {
			throw new ClassCastException(prompt.getOptions().getClass().getName() + " cannot be cast to "
					+ OpenAiChatOptions.class.getName());
		}
	}

	private Script next(Prompt prompt) {
		requireModelOptions(prompt);
		this.prompts.add(prompt);
		Script script = this.scripts.poll();
		if (script != null) {
			return script;
		}
		java.util.function.Function<String, Script> fn = this.responder;
		return (fn != null) ? fn.apply(prompt.getContents()) : this.fallback;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Script script = next(prompt);
		if (script.error() != null) {
			throw script.error();
		}
		if (script.hang() != null) {
			try {
				Thread.sleep(script.hang().toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted", ex);
			}
		}
		String text = String.join("", script.chunks());
		return response(text, usage(prompt, text));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Script script = next(prompt);
		if (script.error() != null) {
			return Flux.error(script.error());
		}
		if (script.hang() != null) {
			return Flux.<ChatResponse>never().take(script.hang());
		}
		String text = String.join("", script.chunks());
		List<ChatResponse> responses = new ArrayList<>();
		for (int i = 0; i < script.chunks().size(); i++) {
			boolean last = i == script.chunks().size() - 1;
			responses.add(response(script.chunks().get(i), last ? usage(prompt, text) : null));
		}
		return Flux.fromIterable(responses);
	}

	private static DefaultUsage usage(Prompt prompt, String text) {
		return new DefaultUsage(prompt.getContents().length(), text.length());
	}

	private static ChatResponse response(String text, DefaultUsage usage) {
		ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model(MODEL);
		if (usage != null) {
			metadata.usage(usage);
		}
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata.build());
	}

}
