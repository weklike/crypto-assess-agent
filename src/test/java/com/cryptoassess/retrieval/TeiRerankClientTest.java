package com.cryptoassess.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TeiRerankClientTest {

	@Test
	void sendsQueryAndTextsAndMapsScoresByIndex() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://tei/rerank"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"query\":\"q\",\"texts\":[\"t0\",\"t1\"],\"raw_scores\":false,\"truncate\":true}"))
			.andRespond(withSuccess("[{\"index\":1,\"score\":0.9},{\"index\":0,\"score\":0.2}]",
					MediaType.APPLICATION_JSON));

		double[] scores = new TeiRerankClient(builder.build()).rerank("q", List.of("t0", "t1"));

		assertThat(scores).containsExactly(0.2, 0.9);
	}

	@Test
	void serverErrorIsDependencyUnavailable() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://tei/rerank")).andRespond(withServerError());

		assertThatThrownBy(() -> new TeiRerankClient(builder.build()).rerank("q", List.of("t0")))
			.isInstanceOf(AppException.class)
			.extracting(ex -> ((AppException) ex).type())
			.isEqualTo(ErrorType.DEPENDENCY_UNAVAILABLE);
	}

	@Test
	void emptyTextsMakeNoCall() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
		MockRestServiceServer.bindTo(builder).build();

		assertThat(new TeiRerankClient(builder.build()).rerank("q", List.of())).isEmpty();
	}

}
