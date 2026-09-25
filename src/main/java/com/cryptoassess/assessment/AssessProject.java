package com.cryptoassess.assessment;

import java.time.Instant;

public record AssessProject(Long id, String name, String systemName, int level, AssessmentStatus status,
		String lastError, int version, Instant createdAt, Instant updatedAt) {

}
