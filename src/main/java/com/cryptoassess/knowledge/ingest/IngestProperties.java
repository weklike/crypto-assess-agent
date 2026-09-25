package com.cryptoassess.knowledge.ingest;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mode sync：接口内同步入库；kafka：写 ingest_job 并发消息，接口返回 202
 * @param backoffInitial 首次重试间隔，之后每次翻倍
 * @param maxRetries 重试次数，用尽后进死信 topic
 * @param sendTimeout 发送消息的超时
 */
@ConfigurationProperties("app.ingest")
public record IngestProperties(String mode, Duration backoffInitial, int maxRetries, Duration sendTimeout) {

	public boolean kafka() {
		return "kafka".equalsIgnoreCase(this.mode);
	}

}
