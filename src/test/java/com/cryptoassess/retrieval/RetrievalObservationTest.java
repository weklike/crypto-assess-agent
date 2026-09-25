package com.cryptoassess.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cryptoassess.support.FakeRerankClient;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

class RetrievalObservationTest {

	@Test
	void everyStageIsObservedWithoutQueryText() {
		List<Observation.Context> stopped = new CopyOnWriteArrayList<>();
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStop(Observation.Context context) {
				stopped.add(context);
			}
		});
		Retriever retriever = (query, filter, size) -> List
			.of(new Candidate("F#1", "1 标题", "路径", "正文", "应用和数据", 1.0, 1));
		HybridSearchService service = new HybridSearchService(retriever, retriever, new FakeRerankClient(),
				new RetrievalProperties("kb_clause", 50, 200, 60, 0.1),
				new RerankProperties("http://tei", "m", Duration.ofSeconds(1), 30), registry);

		service.search(new SearchRequest("身份证号要不要加密", SearchMode.HYBRID_RERANK, 5, null, null));

		assertThat(stopped).extracting(Observation.Context::getName)
			.contains("retrieval.search", "retrieval.bm25", "retrieval.dense", "retrieval.fusion", "retrieval.rerank");
		for (Observation.Context context : stopped) {
			for (KeyValue kv : context.getAllKeyValues()) {
				assertThat(kv.getValue()).doesNotContain("身份证号");
			}
		}
		assertThat(stopped.stream().filter(c -> c.getName().equals("retrieval.search")).findFirst().orElseThrow()
			.getLowCardinalityKeyValue("retrieval.mode").getValue()).isEqualTo("hybrid_rerank");
	}

}
