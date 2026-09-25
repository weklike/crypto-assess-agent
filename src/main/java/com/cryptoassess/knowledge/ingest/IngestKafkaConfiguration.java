package com.cryptoassess.knowledge.ingest;

import com.cryptoassess.knowledge.format.NormalizedFormatException;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.ingest", name = "mode", havingValue = "kafka")
class IngestKafkaConfiguration {

	private static final Logger log = LoggerFactory.getLogger(IngestKafkaConfiguration.class);

	private static final int PARTITIONS = 3;

	@Bean
	NewTopic ingestTopic() {
		return TopicBuilder.name(AsyncIngestService.TOPIC).partitions(PARTITIONS).replicas(1).build();
	}

	/** 死信 topic 与原 topic 分区数相同：DeadLetterPublishingRecoverer 默认写到同一分区号。 */
	@Bean
	NewTopic ingestDeadLetterTopic() {
		return TopicBuilder.name(AsyncIngestService.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
	}

	/**
	 * 指数退避重试 maxRetries 次，用尽后发到 kb.ingest.v1-dlt 并把任务标记为 FAILED；
	 * 格式错误、任务不存在、内容已变这类重试无效的错误直接进死信。
	 */
	@Bean
	DefaultErrorHandler ingestErrorHandler(KafkaOperations<Object, Object> template, IngestJobMapper jobMapper,
			IngestProperties properties) {
		DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(template);
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(properties.maxRetries());
		backOff.setInitialInterval(properties.backoffInitial().toMillis());
		backOff.setMultiplier(2.0);
		DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
			dlt.accept(record, ex);
			try {
				IngestMessage message = IngestMessage.parse(String.valueOf(record.value()));
				Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
				String error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
				jobMapper.fail(message.jobId(), (error.length() > 1000) ? error.substring(0, 1000) : error, true);
			}
			catch (RuntimeException parseError) {
				log.warn("Dead-lettered an unparsable ingest message");
			}
			log.warn("Ingest message with key {} moved to dead letter topic", record.key());
		}, backOff);
		handler.addNotRetryableExceptions(NonRetryableIngestException.class, NormalizedFormatException.class);
		return handler;
	}

}
