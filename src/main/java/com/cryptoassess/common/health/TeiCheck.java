package com.cryptoassess.common.health;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 检查 TEI 就绪，并确认加载的是配置里的重排模型。
 */
class TeiCheck implements DependencyCheck {

	private final RestClient restClient;

	private final String model;

	TeiCheck(RestClient restClient, String model) {
		this.restClient = restClient;
		this.model = model;
	}

	@Override
	public String name() {
		return "tei-rerank";
	}

	@Override
	public CheckResult check() {
		try {
			this.restClient.get().uri("/health").retrieve().toBodilessEntity();
			Info info = this.restClient.get().uri("/info").retrieve().body(Info.class);
			String loaded = (info != null) ? info.modelId() : null;
			if (!servesModel(loaded, this.model)) {
				return CheckResult.down("expected model " + this.model + " but TEI serves " + loaded);
			}
			return CheckResult.up("model " + loaded);
		}
		catch (RestClientException ex) {
			return CheckResult.down(ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

	/**
	 * TEI 从本地快照目录离线启动时，model_id 是 Hugging Face 缓存路径（models--组织--模型/snapshots/版本），
	 * 也视为同一个模型。
	 */
	static boolean servesModel(String loaded, String expected) {
		if (loaded == null) {
			return false;
		}
		if (loaded.equals(expected)) {
			return true;
		}
		String cacheDir = "models--" + expected.replace("/", "--") + "/snapshots/";
		return loaded.contains("/" + cacheDir);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Info(@JsonProperty("model_id") String modelId) {
	}

}
