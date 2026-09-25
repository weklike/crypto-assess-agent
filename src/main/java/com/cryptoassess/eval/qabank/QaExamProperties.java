package com.cryptoassess.eval.qabank;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 题库问答评测的本地模型（环境变量 EVAL_QA_MODEL、EVAL_QA_ALLOWED_HOSTS）。服务地址沿用 spring.ai.ollama.base-url。
 *
 * @param model Ollama 模型名，需事先 ollama pull，评测时不自动拉取
 * @param allowedHosts 允许的服务主机名；默认只有本机和 compose 里的 ollama 服务
 */
@ConfigurationProperties("eval.qa")
public record QaExamProperties(@DefaultValue("qwen2.5:3b") String model,
		@DefaultValue({ "localhost", "127.0.0.1", "::1", "ollama" }) List<String> allowedHosts) {

}
