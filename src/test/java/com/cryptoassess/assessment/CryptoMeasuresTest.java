package com.cryptoassess.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.cryptoassess.common.error.AppException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CryptoMeasuresTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void readsCategoriesWithSnakeCaseKeyManagement() {
		CryptoMeasures measures = JSON.readValue("""
				{"transport":[{"algorithm":"SM4-GCM","protocol":"TLS 1.3","product":"国密 SSL 网关","evidence":"配置截图"}],
				 "key_mgmt":[{"product":"服务器密码机","evidence":"根密钥在密码机中"}]}""", CryptoMeasures.class);

		assertThat(measures.transport()).singleElement()
			.satisfies(m -> assertThat(m.algorithm()).isEqualTo("SM4-GCM"));
		assertThat(measures.keyMgmt()).hasSize(1);
		assertThat(measures.storage()).isEmpty();
		assertThat(measures.all()).hasSize(2);
	}

	@Test
	void rejectsUnknownCategory() {
		assertThatThrownBy(() -> CryptoMeasures.parse("{\"network\":[]}")).isInstanceOf(AppException.class)
			.hasMessageContaining("network");
	}

	@Test
	void rejectsEmptyMeasure() {
		assertThatThrownBy(() -> new CryptoMeasures(List.of(new Measure(" ", null, "", null)), null, null, null, null)
			.validate()).isInstanceOf(AppException.class).hasMessageContaining("transport[0]");
	}

	@Test
	void roundTripsThroughJson() {
		CryptoMeasures measures = new CryptoMeasures(null, List.of(new Measure("SM4", null, null, "字段级加密")), null,
				null, null);

		assertThat(CryptoMeasures.parse(measures.toJson())).isEqualTo(measures.normalized());
	}

}
