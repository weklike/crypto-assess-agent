package com.cryptoassess.knowledge;

public record KbClause(Long id, Long documentId, String docCode, String clauseRef, String clauseNo, String parentNo,
		String path, String title, String body, String layer, String levels, String clauseType, int ordinal,
		String bodySha256) {

}
