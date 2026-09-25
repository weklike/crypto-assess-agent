package com.cryptoassess.common.health;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 只列出本地模型，确认 embedding 模型已拉取；不做向量化调用。
 */
class OllamaCheck implements DependencyCheck {

	private final RestClient restClient;

	private final String model;

	OllamaCheck(RestClient restClient, String model) {
		this.restClient = restClient;
		this.model = model;
	}

	@Override
	public String name() {
		return "ollama";
	}

	@Override
	public CheckResult check() {
		try {
			Tags tags = this.restClient.get().uri("/api/tags").retrieve().body(Tags.class);
			List<String> names = (tags == null || tags.models() == null) ? List.of()
					: tags.models().stream().map(Model::name).toList();
			if (!isPulled(names, this.model)) {
				return CheckResult.down("model " + this.model + " not pulled, available: " + names);
			}
			return CheckResult.up("model " + this.model + " available");
		}
		catch (RestClientException ex) {
			return CheckResult.down(ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

	static boolean isPulled(List<String> names, String model) {
		String wanted = model.contains(":") ? model : model + ":latest";
		return names.contains(wanted);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Tags(List<Model> models) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Model(String name) {
	}

}
