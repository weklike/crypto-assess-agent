package com.cryptoassess.knowledge;

import java.time.Instant;

public record KbDocument(Long id, String docCode, String title, String sourceSha256, String normalizedSha256,
		String parserVersion, String status, int clauseCount, Instant importedAt) {

	public static final String ACTIVE = "ACTIVE";

	public static final String SUPERSEDED = "SUPERSEDED";

}
