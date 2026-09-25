package com.cryptoassess.assessment;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 工作流的一步执行记录。唯一键 (project, object, step, step_key)，重跑时覆盖并累加 attempts。
 */
public record AssessStep(Long id, Long projectId, Long objectId, String step, String stepKey, String status,
		int attempts, @JsonIgnore String outputJson, String error, Instant startedAt, Instant finishedAt) {

	public static final String RUNNING = "RUNNING";

	public static final String SUCCEEDED = "SUCCEEDED";

	public static final String FAILED = "FAILED";

}
