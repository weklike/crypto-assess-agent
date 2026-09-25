package com.cryptoassess.common.llm;

import java.util.Map;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

/**
 * 读取 src/main/resources/prompts/&lt;name&gt;.st。文件名即提示词版本（如 qa-answer.v1），写进 llm_call 与评测报告。
 */
public final class PromptTemplates {

	private PromptTemplates() {
	}

	public static String render(String version, Map<String, Object> variables) {
		ClassPathResource resource = new ClassPathResource("prompts/" + version + ".st");
		if (!resource.exists()) {
			throw new IllegalStateException("prompt template not found: " + version);
		}
		return new PromptTemplate(resource).render(variables);
	}

}
