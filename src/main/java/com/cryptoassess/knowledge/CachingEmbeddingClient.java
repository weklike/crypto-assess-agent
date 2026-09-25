package com.cryptoassess.knowledge;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.cryptoassess.common.error.AppException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 向量缓存：键 = emb:模型名:文本 SHA256，值 = float 数组的 Base64。同一文本、同一模型的向量是确定的，
 * 重建索引和重复查询不必再算一遍。只把未命中的文本交给下层模型。Redis 不可用时返回 503，不绕过缓存。
 */
public class CachingEmbeddingClient implements EmbeddingClient {

	private final EmbeddingClient delegate;

	private final StringRedisTemplate redis;

	private final Duration ttl;

	private final AtomicLong hits = new AtomicLong();

	private final AtomicLong misses = new AtomicLong();

	public CachingEmbeddingClient(EmbeddingClient delegate, StringRedisTemplate redis, Duration ttl) {
		this.delegate = delegate;
		this.redis = redis;
		this.ttl = ttl;
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		if (texts.isEmpty()) {
			return List.of();
		}
		List<String> keys = texts.stream().map(this::key).toList();
		List<String> cached;
		try {
			cached = this.redis.opsForValue().multiGet(keys);
		}
		catch (DataAccessException ex) {
			throw AppException.unavailable("redis", ex);
		}
		float[][] result = new float[texts.size()][];
		Map<String, List<Integer>> missing = new LinkedHashMap<>();
		for (int i = 0; i < texts.size(); i++) {
			String value = (cached == null) ? null : cached.get(i);
			if (value != null) {
				result[i] = decode(value);
				this.hits.incrementAndGet();
			}
			else {
				missing.computeIfAbsent(texts.get(i), t -> new ArrayList<>()).add(i);
				this.misses.incrementAndGet();
			}
		}
		if (!missing.isEmpty()) {
			List<String> toEmbed = new ArrayList<>(missing.keySet());
			List<float[]> vectors = this.delegate.embed(toEmbed);
			Map<String, String> writes = new LinkedHashMap<>();
			for (int j = 0; j < toEmbed.size(); j++) {
				for (int index : missing.get(toEmbed.get(j))) {
					result[index] = vectors.get(j);
				}
				writes.put(key(toEmbed.get(j)), encode(vectors.get(j)));
			}
			try {
				writes.forEach((k, v) -> this.redis.opsForValue().set(k, v, this.ttl));
			}
			catch (DataAccessException ex) {
				throw AppException.unavailable("redis", ex);
			}
		}
		return List.of(result);
	}

	@Override
	public String model() {
		return this.delegate.model();
	}

	@Override
	public Optional<CacheStats> cacheStats() {
		return Optional.of(new CacheStats(this.hits.get(), this.misses.get()));
	}

	private String key(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return "emb:" + this.delegate.model() + ":" + HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String encode(float[] vector) {
		ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
		for (float v : vector) {
			buffer.putFloat(v);
		}
		return Base64.getEncoder().encodeToString(buffer.array());
	}

	static float[] decode(String value) {
		ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
		float[] vector = new float[buffer.remaining() / Float.BYTES];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = buffer.getFloat();
		}
		return vector;
	}

}
