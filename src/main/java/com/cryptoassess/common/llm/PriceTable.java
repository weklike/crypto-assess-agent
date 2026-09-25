package com.cryptoassess.common.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 模型单价表（元 / 百万 tokens），来自 PRICE_TABLE：{@code 模型:输入单价:输出单价;…}。
 * 未配置单价的模型不计费用（cost_cny 为空），评测报告会注明。
 */
public final class PriceTable {

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	private record Price(BigDecimal input, BigDecimal output) {
	}

	private final Map<String, Price> prices;

	private PriceTable(Map<String, Price> prices) {
		this.prices = Map.copyOf(prices);
	}

	public static PriceTable parse(String config) {
		Map<String, Price> prices = new HashMap<>();
		if (config != null && !config.isBlank()) {
			for (String entry : config.split(";")) {
				if (entry.isBlank()) {
					continue;
				}
				String[] parts = entry.strip().split(":");
				if (parts.length != 3) {
					throw new IllegalArgumentException("PRICE_TABLE entry '" + entry.strip() + "' must be model:input:output");
				}
				try {
					BigDecimal input = new BigDecimal(parts[1].strip());
					BigDecimal output = new BigDecimal(parts[2].strip());
					if (input.signum() < 0 || output.signum() < 0) {
						throw new IllegalArgumentException("PRICE_TABLE prices for model " + parts[0].strip() + " must not be negative");
					}
					prices.put(parts[0].strip(), new Price(input, output));
				}
				catch (NumberFormatException ex) {
					throw new IllegalArgumentException("PRICE_TABLE prices for model " + parts[0].strip() + " must be numbers", ex);
				}
			}
		}
		return new PriceTable(prices);
	}

	/** 费用（元，6 位小数）；模型未配置单价或 token 数缺失时为 null。 */
	public BigDecimal cost(String model, Integer inputTokens, Integer outputTokens) {
		Price price = (model == null) ? null : this.prices.get(model);
		if (price == null || inputTokens == null || outputTokens == null) {
			return null;
		}
		return price.input()
			.multiply(BigDecimal.valueOf(inputTokens))
			.add(price.output().multiply(BigDecimal.valueOf(outputTokens)))
			.divide(MILLION, 6, RoundingMode.HALF_UP);
	}

	public boolean isEmpty() {
		return this.prices.isEmpty();
	}

}
