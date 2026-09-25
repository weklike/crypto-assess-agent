package com.cryptoassess.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.knowledge.index.ReindexResult;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.knowledge.normalized-dir=data/fixtures")
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class })
class ClauseIndexerIT {

	@Autowired
	private KnowledgeIngestService ingestService;

	@Autowired
	private ClauseIndexer indexer;

	@Autowired
	private ElasticsearchClient client;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void importFixture() {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		ingestService.importDocument("mock-standard.md");
	}

	@Test
	void indexHoldsEveryClauseAndAliasPointsToIt() throws IOException {
		ReindexResult result = indexer.reindex();

		assertThat(result.documentCount()).isEqualTo(36);
		assertThat(result.indexName()).startsWith("kb_clause_v1_");
		assertThat(client.count(c -> c.index("kb_clause")).count()).isEqualTo(36);
		assertThat(client.indices().getAlias(a -> a.name("kb_clause")).aliases()).containsOnlyKeys(result.indexName());
	}

	@Test
	void documentCarriesMetadataAndVector() throws IOException {
		indexer.reindex();

		@SuppressWarnings("rawtypes")
		GetResponse<Map> doc = client.get(g -> g.index("kb_clause").id("FIX/T 0001-2026#5.2.5"), Map.class);

		assertThat(doc.found()).isTrue();
		Map<?, ?> source = doc.source();
		assertThat(source.get("layer")).isEqualTo("网络和通信");
		assertThat(source.get("levels")).isEqualTo(java.util.List.of("3", "4"));
		assertThat(source.get("doc_code")).isEqualTo("FIX/T 0001-2026");
		assertThat(source.get("path")).isEqualTo("5 技术要求 > 5.2 网络和通信");
		// ES 9 默认不在 _source 里返回 dense_vector；用条款自己的向量做 kNN，应当排第一
		KbClause clause = ingestService.findClause("FIX/T 0001-2026#5.2.5").orElseThrow();
		float[] vector = FakeEmbeddingModel.vector(ClauseTexts.embeddingText(clause), 1024);
		java.util.List<Float> query = new java.util.ArrayList<>();
		for (float v : vector) {
			query.add(v);
		}
		@SuppressWarnings("rawtypes")
		var hits = client.search(s -> s.index("kb_clause")
			.knn(k -> k.field("embedding").queryVector(query).k(1).numCandidates(50)), Map.class).hits().hits();
		assertThat(hits).first().extracting(h -> h.id()).isEqualTo("FIX/T 0001-2026#5.2.5");
	}

	@Test
	void rebuildSwitchesAliasAndDropsOldIndex() throws IOException {
		ReindexResult first = indexer.reindex();
		ReindexResult second = indexer.reindex();

		assertThat(second.indexName()).isNotEqualTo(first.indexName());
		assertThat(client.indices().getAlias(a -> a.name("kb_clause")).aliases()).containsOnlyKeys(second.indexName());
		assertThat(client.indices().exists(e -> e.index(first.indexName())).value()).isFalse();
		assertThat(client.count(c -> c.index("kb_clause")).count()).isEqualTo(36);
	}

}
