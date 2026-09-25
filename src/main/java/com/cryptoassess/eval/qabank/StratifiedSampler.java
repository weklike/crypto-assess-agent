package com.cryptoassess.eval.qabank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 按题型分层抽样：各题型按占比分配名额（最大余数法保证总数准确），层内用固定种子打乱后取前若干。
 * 分层保证多选、判断这类少数题型不会因为随机波动几乎抽不到。
 */
public final class StratifiedSampler {

	private StratifiedSampler() {
	}

	public static List<QaBankItem> sample(List<QaBankItem> bank, int size, long seed) {
		if (size > bank.size()) {
			throw new IllegalArgumentException("sample size " + size + " exceeds bank size " + bank.size());
		}
		Map<String, List<QaBankItem>> strata = bank.stream()
			.collect(Collectors.groupingBy(QaBankItem::type, TreeMap::new, Collectors.toList()));
		Map<String, Integer> quotas = allocate(strata, bank.size(), size);
		Random random = new Random(seed);
		List<QaBankItem> sample = new ArrayList<>();
		strata.forEach((type, items) -> {
			List<QaBankItem> shuffled = new ArrayList<>(items);
			shuffled.sort(Comparator.comparing(QaBankItem::id));
			Collections.shuffle(shuffled, random);
			sample.addAll(shuffled.subList(0, quotas.get(type)));
		});
		sample.sort(Comparator.comparing(QaBankItem::id));
		return sample;
	}

	private static Map<String, Integer> allocate(Map<String, List<QaBankItem>> strata, int total, int size) {
		Map<String, Integer> quotas = new TreeMap<>();
		Map<String, Double> remainders = new TreeMap<>();
		int allocated = 0;
		for (Map.Entry<String, List<QaBankItem>> e : strata.entrySet()) {
			double exact = (double) size * e.getValue().size() / total;
			int floor = (int) Math.floor(exact);
			quotas.put(e.getKey(), floor);
			remainders.put(e.getKey(), exact - floor);
			allocated += floor;
		}
		List<String> byRemainder = remainders.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.map(Map.Entry::getKey)
			.toList();
		for (int i = 0; allocated < size; i++, allocated++) {
			String type = byRemainder.get(i % byRemainder.size());
			quotas.merge(type, 1, Integer::sum);
		}
		return quotas;
	}

}
