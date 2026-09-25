package com.cryptoassess.knowledge.ingest;

import java.time.Instant;

public record IngestJob(Long id, String fileName, String normalizedSha256, String parserVersion, Long documentId,
		String status, int attempts, String lastError, Instant createdAt, Instant updatedAt) {

	public static final String PENDING = "PENDING";

	public static final String RUNNING = "RUNNING";

	public static final String SUCCEEDED = "SUCCEEDED";

	public static final String FAILED = "FAILED";

}
