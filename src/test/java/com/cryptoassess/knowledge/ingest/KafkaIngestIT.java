package com.cryptoassess.knowledge.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeEmbeddingModel;
import com.cryptoassess.support.FakeRerankConfiguration;
import com.cryptoassess.support.KafkaTestcontainers;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = { "app.ingest.mode=kafka", "app.ingest.backoff-initial=100ms",
		"app.workflow.resume-on-startup=false" })
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, KafkaTestcontainers.class,
		FakeAiConfiguration.class, FakeRerankConfiguration.class })
class KafkaIngestIT {

	static final Path normalizedDir = createDir();

	@Autowired
	private AsyncIngestService ingestService;

	@Autowired
	private IngestConsumer consumer;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private FakeEmbeddingModel embeddingModel;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private KafkaConnectionDetails kafkaConnection;

	@DynamicPropertySource
	static void dirs(DynamicPropertyRegistry registry) {
		registry.add("app.knowledge.normalized-dir", normalizedDir::toString);
	}

	private static Path createDir() {
		try {
			return Files.createTempDirectory("normalized");
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM ingest_job");
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
	}

	@AfterEach
	void restore() {
		embeddingModel.setFailing(false);
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("condition not met in time");
			}
			Thread.sleep(100);
		}
	}

	private String status(long jobId) {
		return jdbcTemplate.queryForObject("SELECT status FROM ingest_job WHERE id = ?", String.class, jobId);
	}

	@Test
	void sameMessageDeliveredThreeTimesIsIngestedOnce() throws Exception {
		Files.copy(Path.of("data/fixtures/mock-standard.md"), normalizedDir.resolve("a.md"),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		long processedBefore = consumer.processedCount();

		IngestJob job = ingestService.submit("a.md").job();
		await(() -> "SUCCEEDED".equals(status(job.id())));
		String payload = IngestMessage.of(job).toJson();
		kafkaTemplate.send(AsyncIngestService.TOPIC, job.normalizedSha256(), payload).get();
		kafkaTemplate.send(AsyncIngestService.TOPIC, job.normalizedSha256(), payload).get();
		await(() -> consumer.processedCount() >= processedBefore + 3);

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_document", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM ingest_job WHERE id = ?", Integer.class, job.id()))
			.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT document_id FROM ingest_job WHERE id = ?", Long.class, job.id()))
			.isNotNull();
		// 再次提交相同内容：直接返回已成功的任务，不再发消息
		AsyncIngestService.Submission again = ingestService.submit("a.md");
		assertThat(again.accepted()).isFalse();
		assertThat(again.job().id()).isEqualTo(job.id());
	}

	@Test
	void threeFailedRetriesGoToDeadLetterTopic() throws Exception {
		Files.writeString(normalizedDir.resolve("b.md"),
				Files.readString(Path.of("data/fixtures/mock-standard.md")).replace("本仿标准中的密码保护", "死信测试"));
		embeddingModel.setFailing(true);

		IngestJob job = ingestService.submit("b.md").job();
		await(() -> "FAILED".equals(status(job.id())));

		assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM ingest_job WHERE id = ?", Integer.class, job.id()))
			.isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject("SELECT last_error FROM ingest_job WHERE id = ?", String.class, job.id()))
			.contains("embedding");
		try (KafkaConsumer<String, String> dlt = new KafkaConsumer<>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
				String.join(",", kafkaConnection.getBootstrapServers()), ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader", ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
				"earliest"), new StringDeserializer(), new StringDeserializer())) {
			dlt.subscribe(List.of(AsyncIngestService.TOPIC + "-dlt"));
			List<ConsumerRecord<String, String>> records = new java.util.ArrayList<>();
			long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
			while (records.isEmpty() && System.nanoTime() < deadline) {
				dlt.poll(Duration.ofMillis(500)).forEach(records::add);
			}
			assertThat(records).anySatisfy(r -> assertThat(r.key()).isEqualTo(job.normalizedSha256()));
		}

		// 故障恢复后重新提交，失败的任务回到 PENDING 并成功
		embeddingModel.setFailing(false);
		AsyncIngestService.Submission retry = ingestService.submit("b.md");
		assertThat(retry.accepted()).isTrue();
		await(() -> "SUCCEEDED".equals(status(job.id())));
	}

	@Test
	void formatErrorIsRejectedBeforeQueueing() throws Exception {
		Files.writeString(normalizedDir.resolve("broken.md"), "no front matter\n");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> ingestService.submit("broken.md"))
			.isInstanceOf(com.cryptoassess.knowledge.format.NormalizedFormatException.class);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ingest_job", Integer.class)).isZero();
	}

}
