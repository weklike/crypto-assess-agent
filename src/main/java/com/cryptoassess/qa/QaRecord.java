package com.cryptoassess.qa;

import java.time.Instant;

public record QaRecord(String question, String mode, String answer, String citationsJson, int invalidCitationCount,
		boolean refused, Double topScore, String promptVersion, Long llmCallId, Integer firstTokenMs,
		Integer latencyMs, Instant createdAt) {

}
