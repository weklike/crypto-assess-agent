package com.cryptoassess.retrieval;

import java.util.List;

import com.cryptoassess.common.error.AppException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 调用 TEI 的 /rerank。raw_scores=false 时分数经过 sigmoid，落在 [0, 1]，拒答阈值基于这个尺度。
 */
public class TeiRerankClient implements RerankClient {

	private final RestClient restClient;

	public TeiRerankClient(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public double[] rerank(String query, List<String> texts) {
		if (texts.isEmpty()) {
			return new double[0];
		}
		RerankResult[] results;
		try {
			results = this.restClient.post()
				.uri("/rerank")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RerankRequest(query, texts, false, true))
				.retrieve()
				.body(RerankResult[].class);
		}
		catch (RestClientException ex) {
			throw AppException.unavailable("tei-rerank", ex);
		}
		if (results == null || results.length != texts.size()) {
			throw AppException.unavailable("tei-rerank",
					new IllegalStateException("unexpected rerank result size " + (results == null ? 0 : results.length)));
		}
		double[] scores = new double[texts.size()];
		for (RerankResult result : results) {
			scores[result.index()] = result.score();
		}
		return scores;
	}

	record RerankRequest(String query, List<String> texts, @JsonProperty("raw_scores") boolean rawScores,
			boolean truncate) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RerankResult(int index, double score) {
	}

}
