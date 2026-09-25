package com.cryptoassess.knowledge.index;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.cryptoassess.common.error.AppException;
import com.cryptoassess.knowledge.ClauseTexts;
import com.cryptoassess.knowledge.EmbeddingClient;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.retrieval.RetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 全量重建条款索引：新建带时间戳的物理索引 → 写入 → 校验文档数 → 原子切换别名 → 删除旧索引。
 * 任何一步失败都删掉半成品，别名仍指向旧索引，检索不受影响。
 */
@Service
public class ClauseIndexer {

	private static final Logger log = LoggerFactory.getLogger(ClauseIndexer.class);

	/** 映射版本；改映射时新建 v2 的 JSON 并改这里 */
	static final String MAPPING_VERSION = "kb_clause_v1";

	private static final String MAPPING_RESOURCE = "es/" + MAPPING_VERSION + ".json";

	private static final int BULK_SIZE = 200;

	private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd't'HHmmssSSS");

	private final ElasticsearchClient client;

	private final KbClauseMapper clauseMapper;

	private final EmbeddingClient embeddingClient;

	private final String alias;

	public ClauseIndexer(ElasticsearchClient client, KbClauseMapper clauseMapper, EmbeddingClient embeddingClient,
			RetrievalProperties retrievalProperties) {
		this.client = client;
		this.clauseMapper = clauseMapper;
		this.embeddingClient = embeddingClient;
		this.alias = retrievalProperties.indexAlias();
	}

	public synchronized ReindexResult reindex() {
		long start = System.nanoTime();
		List<KbClause> clauses = this.clauseMapper.findActive();
		// 先向量化再建索引：模型不可用时不会留下空索引
		List<float[]> vectors = this.embeddingClient.embed(clauses.stream().map(ClauseTexts::embeddingText).toList());
		String indexName = MAPPING_VERSION + "_" + ZonedDateTime.now(ZoneOffset.UTC).format(SUFFIX);
		try {
			createIndex(indexName);
			try {
				bulkIndex(indexName, clauses, vectors);
				this.client.indices().refresh(r -> r.index(indexName));
				long count = this.client.count(c -> c.index(indexName)).count();
				if (count != clauses.size()) {
					throw new IllegalStateException("index " + indexName + " has " + count + " documents, expected "
							+ clauses.size());
				}
				List<String> previous = switchAlias(indexName);
				if (!previous.isEmpty()) {
					this.client.indices().delete(d -> d.index(previous));
				}
				long tookMs = (System.nanoTime() - start) / 1_000_000;
				log.info("Reindexed {} clauses into {} in {} ms", count, indexName, tookMs);
				return new ReindexResult(indexName, this.alias, count, this.embeddingClient.model(), tookMs);
			}
			catch (IOException | RuntimeException ex) {
				deleteQuietly(indexName);
				throw ex;
			}
		}
		catch (IOException | ElasticsearchException ex) {
			throw AppException.unavailable("elasticsearch", ex);
		}
	}

	/** 别名当前指向的物理索引（写进评测报告）；别名不存在时为空。 */
	public Optional<String> currentIndex() {
		try {
			if (!this.client.indices().existsAlias(e -> e.name(this.alias)).value()) {
				return Optional.empty();
			}
			return this.client.indices().getAlias(a -> a.name(this.alias)).aliases().keySet().stream().findFirst();
		}
		catch (IOException | ElasticsearchException ex) {
			throw AppException.unavailable("elasticsearch", ex);
		}
	}

	private void createIndex(String indexName) throws IOException {
		try (InputStream mapping = new ClassPathResource(MAPPING_RESOURCE).getInputStream()) {
			this.client.indices().create(c -> c.index(indexName).withJson(mapping));
		}
	}

	private void bulkIndex(String indexName, List<KbClause> clauses, List<float[]> vectors) throws IOException {
		for (int from = 0; from < clauses.size(); from += BULK_SIZE) {
			BulkRequest.Builder bulk = new BulkRequest.Builder();
			for (int i = from; i < Math.min(from + BULK_SIZE, clauses.size()); i++) {
				ClauseDocument document = toDocument(clauses.get(i), vectors.get(i));
				bulk.operations(op -> op.index(idx -> idx.index(indexName).id(document.clauseRef()).document(document)));
			}
			BulkResponse response = this.client.bulk(bulk.build());
			if (response.errors()) {
				String reason = response.items()
					.stream()
					.filter(item -> item.error() != null)
					.findFirst()
					.map(item -> item.id() + ": " + item.error().reason())
					.orElse("unknown");
				throw new IllegalStateException("bulk indexing failed, first error " + reason);
			}
		}
	}

	/** 把别名原子地切到新索引，返回之前挂着别名的旧索引。 */
	private List<String> switchAlias(String indexName) throws IOException {
		List<String> previous = new ArrayList<>();
		if (this.client.indices().existsAlias(e -> e.name(this.alias)).value()) {
			previous.addAll(this.client.indices().getAlias(a -> a.name(this.alias)).aliases().keySet());
		}
		this.client.indices().updateAliases(u -> {
			previous.forEach(old -> u.actions(a -> a.remove(r -> r.index(old).alias(this.alias))));
			return u.actions(a -> a.add(add -> add.index(indexName).alias(this.alias)));
		});
		return previous;
	}

	private void deleteQuietly(String indexName) {
		try {
			if (this.client.indices().exists(e -> e.index(indexName)).value()) {
				this.client.indices().delete(d -> d.index(indexName));
			}
		}
		catch (IOException | ElasticsearchException ex) {
			log.warn("Failed to clean up partial index {}: {}", indexName, ex.toString());
		}
	}

	private static ClauseDocument toDocument(KbClause clause, float[] vector) {
		List<String> levels = (clause.levels() == null) ? List.of() : Arrays.asList(clause.levels().split(","));
		return new ClauseDocument(clause.clauseRef(), clause.docCode(), clause.clauseNo(), clause.path(), clause.title(),
				clause.body(), clause.layer(), levels, clause.clauseType(), vector);
	}

}
