package com.cryptoassess.eval.metrics;

import java.util.List;

public final class Percentiles {

	private Percentiles() {
	}

	/** 最近秩法：排序后取第 ceil(p/100 × n) 个值；空列表返回 null。 */
	public static Long nearestRank(List<Long> values, int percentile) {
		if (values.isEmpty()) {
			return null;
		}
		List<Long> sorted = values.stream().sorted().toList();
		int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
		return sorted.get(Math.max(0, rank - 1));
	}

}
