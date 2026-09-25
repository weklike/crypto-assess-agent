package com.cryptoassess.knowledge.format;

import java.util.List;

public record ParsedStandard(String docCode, String title, String sourceSha256, String normalizedBy,
		String normalizedSha256, List<ClauseRecord> clauses) {

}
