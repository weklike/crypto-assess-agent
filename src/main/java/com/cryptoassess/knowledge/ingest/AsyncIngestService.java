package com.cryptoassess.knowledge.ingest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.cryptoassess.common.db.GeneratedKey;
import com.cryptoassess.common.error.AppException;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.format.NormalizedStandardParser;
import com.cryptoassess.knowledge.format.ParsedStandard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 异步入库的提交端：同步做格式校验（错误立即 400），按 (normalized_sha256, parser_version) 建任务，发消息。
 * 相同内容已成功入库时直接返回原任务、不再发消息；失败过的任务重新排队。
 */
@Service
@ConditionalOnProperty(prefix = "app.ingest", name = "mode", havingValue = "kafka")
public class AsyncIngestService {

	public static final String TOPIC = "kb.ingest.v1";

	/**
	 * @param accepted true 表示已排队（返回 202）；false 表示内容已入库，无需处理
	 */
	public record Submission(IngestJob job, boolean accepted) {
	}

	private final KnowledgeIngestService ingestService;

	private final IngestJobMapper jobMapper;

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final IngestProperties properties;

	public AsyncIngestService(KnowledgeIngestService ingestService, IngestJobMapper jobMapper,
			KafkaTemplate<String, String> kafkaTemplate, IngestProperties properties) {
		this.ingestService = ingestService;
		this.jobMapper = jobMapper;
		this.kafkaTemplate = kafkaTemplate;
		this.properties = properties;
	}

	public Submission submit(String fileName) {
		ParsedStandard parsed = this.ingestService.parse(fileName);
		String sha = parsed.normalizedSha256();
		String parserVersion = NormalizedStandardParser.PARSER_VERSION;
		IngestJob job = this.jobMapper.findByContent(sha, parserVersion);
		if (job == null) {
			try {
				GeneratedKey key = new GeneratedKey();
				this.jobMapper.insert(fileName, sha, parserVersion, key);
				job = this.jobMapper.findById(key.getId());
			}
			catch (DuplicateKeyException ex) {
				// 并发提交同一内容：以已存在的任务为准
				return new Submission(this.jobMapper.findByContent(sha, parserVersion), true);
			}
		}
		else if (IngestJob.SUCCEEDED.equals(job.status())) {
			return new Submission(job, false);
		}
		else if (IngestJob.FAILED.equals(job.status())) {
			this.jobMapper.requeue(job.id(), fileName);
			job = this.jobMapper.findById(job.id());
		}
		else {
			return new Submission(job, true);
		}
		publish(job);
		return new Submission(job, true);
	}

	public IngestJob job(long id) {
		IngestJob job = this.jobMapper.findById(id);
		if (job == null) {
			throw AppException.notFound("ingest job " + id + " not found");
		}
		return job;
	}

	private void publish(IngestJob job) {
		try {
			this.kafkaTemplate.send(TOPIC, job.normalizedSha256(), IngestMessage.of(job).toJson())
				.get(this.properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException | TimeoutException ex) {
			this.jobMapper.fail(job.id(), "failed to publish message: " + ex.getClass().getSimpleName(), true);
			throw AppException.unavailable("kafka", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw AppException.unavailable("kafka", ex);
		}
	}

}
