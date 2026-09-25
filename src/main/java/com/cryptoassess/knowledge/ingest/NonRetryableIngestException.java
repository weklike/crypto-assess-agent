package com.cryptoassess.knowledge.ingest;

/**
 * 重试也不会成功的失败（任务不存在、文件内容已变），直接进死信。
 */
public class NonRetryableIngestException extends RuntimeException {

	public NonRetryableIngestException(String message) {
		super(message);
	}

}
