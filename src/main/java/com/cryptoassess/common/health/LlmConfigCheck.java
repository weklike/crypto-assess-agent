package com.cryptoassess.common.health;

/**
 * 只检查大模型配置是否齐全，不发起调用：健康检查会被频繁请求，调用模型既花钱又慢。
 */
class LlmConfigCheck implements DependencyCheck {

	private final String apiKey;

	private final String model;

	LlmConfigCheck(String apiKey, String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public String name() {
		return "llm-config";
	}

	@Override
	public CheckResult check() {
		if (this.apiKey == null || this.apiKey.isBlank()) {
			return CheckResult.down("LLM_API_KEY is not configured");
		}
		if (this.model == null || this.model.isBlank()) {
			return CheckResult.down("LLM_MODEL is not configured");
		}
		return CheckResult.up("model " + this.model + " configured (not called)");
	}

}
