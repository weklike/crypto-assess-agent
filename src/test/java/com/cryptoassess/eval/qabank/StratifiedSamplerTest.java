package com.cryptoassess.eval.qabank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class StratifiedSamplerTest {

	private static List<QaBankItem> bank() {
		List<QaBankItem> items = new ArrayList<>();
		for (int i = 0; i < 600; i++) {
			items.add(item("s" + i, "single"));
		}
		for (int i = 0; i < 300; i++) {
			items.add(item("m" + i, "multi"));
		}
		for (int i = 0; i < 100; i++) {
			items.add(item("j" + i, "judge"));
		}
		return items;
	}

	private static QaBankItem item(String id, String type) {
		return new QaBankItem(id, type, "stem", Map.of("A", "a", "B", "b"), List.of("A"));
	}

	@Test
	void allocatesProportionallyByType() {
		List<QaBankItem> sample = StratifiedSampler.sample(bank(), 200, 20260924L);

		Map<String, Long> byType = sample.stream().collect(Collectors.groupingBy(QaBankItem::type, Collectors.counting()));
		assertThat(sample).hasSize(200);
		assertThat(byType).containsEntry("single", 120L).containsEntry("multi", 60L).containsEntry("judge", 20L);
		assertThat(sample).extracting(QaBankItem::id).doesNotHaveDuplicates();
	}

	@Test
	void sameSeedSameSampleDifferentSeedDifferentSample() {
		List<String> a = StratifiedSampler.sample(bank(), 50, 1L).stream().map(QaBankItem::id).toList();
		List<String> b = StratifiedSampler.sample(bank(), 50, 1L).stream().map(QaBankItem::id).toList();
		List<String> c = StratifiedSampler.sample(bank(), 50, 2L).stream().map(QaBankItem::id).toList();

		assertThat(a).isEqualTo(b).isNotEqualTo(c);
	}

	@Test
	void largestRemainderKeepsTotalExact() {
		// 7:2:1 抽 11 → 7.7 / 2.2 / 1.1 → 7/2/1 + 余数最大的 single 多 1
		List<QaBankItem> items = new ArrayList<>();
		for (int i = 0; i < 70; i++) {
			items.add(item("s" + i, "single"));
		}
		for (int i = 0; i < 20; i++) {
			items.add(item("m" + i, "multi"));
		}
		for (int i = 0; i < 10; i++) {
			items.add(item("j" + i, "judge"));
		}

		Map<String, Long> byType = StratifiedSampler.sample(items, 11, 7L)
			.stream()
			.collect(Collectors.groupingBy(QaBankItem::type, Collectors.counting()));

		assertThat(byType).containsEntry("single", 8L).containsEntry("multi", 2L).containsEntry("judge", 1L);
	}

}
