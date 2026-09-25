package com.cryptoassess.knowledge;

import java.util.List;

/**
 * 文本向量化。实现负责分批、超时和维度校验；T19 在外面再包一层 Redis 缓存。
 */
public interface EmbeddingClient {

	List<float[]> embed(List<String> texts);

	default float[] embed(String text) {
		return embed(List.of(text)).get(0);
	}

	/** 模型名，写进评测报告和缓存键 */
	String model();

	/** 缓存命中统计；没有缓存时为空。 */
	default java.util.Optional<CacheStats> cacheStats() {
		return java.util.Optional.empty();
	}

	record CacheStats(long hits, long misses) {

		public double hitRate() {
			long total = this.hits + this.misses;
			return (total == 0) ? 0 : (double) this.hits / total;
		}

	}

}
