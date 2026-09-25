package com.cryptoassess.common.llm;

import com.cryptoassess.common.db.GeneratedKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 所有模型调用都写 llm_call：成功、超时、空回复、输出不合规都记。不记录提示词和回答原文。
 */
@Service
public class LlmCallRecorder {

	private final LlmCallMapper mapper;

	private final PriceTable prices;

	public LlmCallRecorder(LlmCallMapper mapper, PriceTable prices) {
		this.mapper = mapper;
		this.prices = prices;
	}

	/** 写入调用记录；费用按 PRICE_TABLE 计算（调用方已给出时不覆盖）。 */
	@Transactional
	public long record(LlmCall call) {
		LlmCall withCost = (call.costCny() != null) ? call
				: new LlmCall(call.purpose(), call.model(), call.promptVersion(), call.latencyMs(), call.inputTokens(),
						call.outputTokens(), this.prices.cost(call.model(), call.inputTokens(), call.outputTokens()),
						call.status(), call.errorCode(), call.createdAt());
		GeneratedKey key = new GeneratedKey();
		this.mapper.insert(withCost, key);
		return key.getId();
	}

}
