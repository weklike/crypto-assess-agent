package com.cryptoassess.eval.qabank;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 题库只发往本地模型（数据边界约定）：地址的主机名必须在白名单里，且不能是 Ollama 的 *-cloud 模型
 * （这类模型经本地服务转发到 ollama.com）。不满足时拒绝运行，不回退到其他模型。
 */
final class LocalModelGuard {

	private LocalModelGuard() {
	}

	static void check(String baseUrl, String model, List<String> allowedHosts) {
		if (model == null || model.isBlank()) {
			throw new IllegalStateException("eval.qa.model is required for the local exam model");
		}
		String name = model.strip().toLowerCase(Locale.ROOT);
		if (name.endsWith("-cloud") || name.endsWith(":cloud")) {
			throw new IllegalStateException("question bank must stay local; Ollama cloud model " + model + " is refused");
		}
		String host;
		try {
			URI uri = URI.create(baseUrl.strip());
			String scheme = (uri.getScheme() == null) ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				throw new IllegalStateException("question bank must stay local; unsupported base URL scheme: " + baseUrl);
			}
			host = uri.getHost();
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new IllegalStateException("question bank must stay local; invalid base URL: " + baseUrl, ex);
		}
		if (host == null) {
			throw new IllegalStateException("question bank must stay local; base URL has no host: " + baseUrl);
		}
		String normalized = host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
		if (allowedHosts.stream().noneMatch(h -> h.toLowerCase(Locale.ROOT).equals(normalized))) {
			throw new IllegalStateException("question bank must stay local; host " + host + " is not in eval.qa.allowed-hosts "
					+ allowedHosts);
		}
	}

}
