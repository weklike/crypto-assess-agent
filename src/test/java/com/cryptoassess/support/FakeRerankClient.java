package com.cryptoassess.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.retrieval.RerankClient;

/**
 * 确定性的假重排：分数 = 查询中出现在文本里的不同字符占比，范围 [0, 1]。可切换成“服务不可用”。
 */
public class FakeRerankClient implements RerankClient {

	private final AtomicBoolean available = new AtomicBoolean(true);

	private final AtomicInteger calls = new AtomicInteger();

	@Override
	public double[] rerank(String query, List<String> texts) {
		this.calls.incrementAndGet();
		if (!this.available.get()) {
			throw AppException.unavailable("tei-rerank", new IllegalStateException("fake rerank is down"));
		}
		Set<Integer> queryChars = query.codePoints()
			.filter(Character::isLetterOrDigit)
			.boxed()
			.collect(Collectors.toSet());
		double[] scores = new double[texts.size()];
		for (int i = 0; i < texts.size(); i++) {
			Set<Integer> textChars = texts.get(i).codePoints().boxed().collect(Collectors.toSet());
			long shared = queryChars.stream().filter(textChars::contains).count();
			scores[i] = queryChars.isEmpty() ? 0 : (double) shared / queryChars.size();
		}
		return scores;
	}

	public void setAvailable(boolean available) {
		this.available.set(available);
	}

	public int calls() {
		return this.calls.get();
	}

}
