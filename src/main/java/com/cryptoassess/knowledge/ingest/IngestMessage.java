package com.cryptoassess.knowledge.ingest;

import tools.jackson.databind.json.JsonMapper;

/**
 * 入库消息（topic kb.ingest.v1，key = normalized_sha256）。只带任务标识，状态以 ingest_job 为准。
 */
public record IngestMessage(long jobId, String fileName, String normalizedSha256, String parserVersion) {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	public static IngestMessage of(IngestJob job) {
		return new IngestMessage(job.id(), job.fileName(), job.normalizedSha256(), job.parserVersion());
	}

	public String toJson() {
		return JSON.writeValueAsString(this);
	}

	public static IngestMessage parse(String json) {
		return JSON.readValue(json, IngestMessage.class);
	}

}
