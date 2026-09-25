package com.cryptoassess.assessment;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * @param scope object / layer / total
 * @param score 全部不适用时为 null
 */
public record AssessScore(Long id, Long projectId, String scope, String scopeKey, BigDecimal score,
		@JsonRawValue String detailJson, String ruleVersion, Instant createdAt) {

}
