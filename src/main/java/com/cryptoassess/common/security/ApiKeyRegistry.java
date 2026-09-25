package com.cryptoassess.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * API Key 注册表，配置格式：{@code 客户端:密钥:scope1,scope2;客户端2:密钥2:scope}。
 * 只保存密钥的 SHA-256，比较用 {@link MessageDigest#isEqual}（常数时间）；报错信息里不出现密钥。
 */
public final class ApiKeyRegistry {

	public static final Set<String> SCOPES = Set.of("kb:read", "rules:read", "admin");

	private static final int MIN_KEY_LENGTH = 12;

	public record ApiClient(String clientId, Set<String> scopes) {
	}

	private final Map<String, ApiClient> clientsByKeyHash;

	private ApiKeyRegistry(Map<String, ApiClient> clientsByKeyHash) {
		this.clientsByKeyHash = Map.copyOf(clientsByKeyHash);
	}

	public static ApiKeyRegistry parse(String config) {
		Map<String, ApiClient> clients = new HashMap<>();
		Set<String> ids = new HashSet<>();
		if (config == null || config.isBlank()) {
			return new ApiKeyRegistry(clients);
		}
		String[] entries = config.split(";");
		for (int i = 0; i < entries.length; i++) {
			String entry = entries[i].strip();
			if (entry.isEmpty()) {
				continue;
			}
			int first = entry.indexOf(':');
			int second = (first < 0) ? -1 : entry.indexOf(':', first + 1);
			if (second < 0) {
				throw new IllegalArgumentException("api key entry #" + (i + 1) + " must be client:key:scopes");
			}
			String client = entry.substring(0, first).strip();
			String key = entry.substring(first + 1, second).strip();
			String scopeText = entry.substring(second + 1).strip();
			if (client.isEmpty() || !ids.add(client)) {
				throw new IllegalArgumentException("api key entry #" + (i + 1) + " has an empty or duplicate client id");
			}
			if (key.length() < MIN_KEY_LENGTH) {
				throw new IllegalArgumentException("api key for client " + client + " is shorter than " + MIN_KEY_LENGTH);
			}
			Set<String> scopes = new LinkedHashSet<>();
			for (String scope : scopeText.split(",")) {
				String s = scope.strip();
				if (!SCOPES.contains(s)) {
					throw new IllegalArgumentException("client " + client + " has unknown scope '" + s + "', allowed " + SCOPES);
				}
				scopes.add(s);
			}
			if (clients.putIfAbsent(hash(key), new ApiClient(client, Set.copyOf(scopes))) != null) {
				throw new IllegalArgumentException("client " + client + " reuses a key of another client");
			}
		}
		return new ApiKeyRegistry(clients);
	}

	public Optional<ApiClient> authenticate(String key) {
		if (key == null || key.isBlank()) {
			return Optional.empty();
		}
		byte[] candidate = hashBytes(key.strip());
		for (Map.Entry<String, ApiClient> e : this.clientsByKeyHash.entrySet()) {
			if (MessageDigest.isEqual(candidate, java.util.HexFormat.of().parseHex(e.getKey()))) {
				return Optional.of(e.getValue());
			}
		}
		return Optional.empty();
	}

	public int size() {
		return this.clientsByKeyHash.size();
	}

	private static String hash(String key) {
		return java.util.HexFormat.of().formatHex(hashBytes(key));
	}

	private static byte[] hashBytes(String key) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
