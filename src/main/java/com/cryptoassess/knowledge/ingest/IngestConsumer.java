package com.cryptoassess.knowledge.ingest;

import java.util.concurrent.atomic.AtomicLong;

import com.cryptoassess.knowledge.ImportResult;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 入库消费者：解析、写库、向量化、重建索引。消息至少投递一次，靠 ingest_job 的状态保证幂等：
 * 已成功的任务直接确认；每次尝试先 claim（RUNNING、attempts+1），失败抛异常交给 DefaultErrorHandler 重试或进死信。
 */
@Component
@ConditionalOnProperty(prefix = "app.ingest", name = "mode", havingValue = "kafka")
public class IngestConsumer {

	private static final Logger log = LoggerFactory.getLogger(IngestConsumer.class);

	private final IngestJobMapper jobMapper;

	private final KnowledgeIngestService ingestService;

	private final ClauseIndexer indexer;

	private final AtomicLong processed = new AtomicLong();

	public IngestConsumer(IngestJobMapper jobMapper, KnowledgeIngestService ingestService, ClauseIndexer indexer) {
		this.jobMapper = jobMapper;
		this.ingestService = ingestService;
		this.indexer = indexer;
	}

	@KafkaListener(id = "kb-ingest", topics = AsyncIngestService.TOPIC, groupId = "crypto-assess-ingest")
	public void onMessage(String payload) {
		try {
			handle(IngestMessage.parse(payload));
		}
		finally {
			this.processed.incrementAndGet();
		}
	}

	private void handle(IngestMessage message) {
		IngestJob job = this.jobMapper.findById(message.jobId());
		if (job == null) {
			throw new NonRetryableIngestException("ingest job " + message.jobId() + " does not exist");
		}
		if (IngestJob.SUCCEEDED.equals(job.status()) || this.jobMapper.claim(job.id()) == 0) {
			log.info("Ingest job {} already done, acknowledging duplicate message", job.id());
			return;
		}
		try {
			ImportResult result = this.ingestService.importDocument(message.fileName());
			if (!result.normalizedSha256().equals(message.normalizedSha256())) {
				throw new NonRetryableIngestException("file " + message.fileName() + " changed after submission");
			}
			this.indexer.reindex();
			this.jobMapper.succeed(job.id(), result.documentId());
			log.info("Ingest job {} succeeded", job.id());
		}
		catch (RuntimeException ex) {
			this.jobMapper.fail(job.id(), truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()), false);
			throw ex;
		}
	}

	/** 已处理的消息数（含重复消息），测试用来等待消费完成。 */
	public long processedCount() {
		return this.processed.get();
	}

	private static String truncate(String text) {
		return (text.length() <= 1000) ? text : text.substring(0, 1000);
	}

}
