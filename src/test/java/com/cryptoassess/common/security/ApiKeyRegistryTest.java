package com.cryptoassess.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeyRegistryTest {

	@Test
	void parsesClientsKeysAndScopes() {
		ApiKeyRegistry registry = ApiKeyRegistry.parse("claude:k-123456789012:kb:read,rules:read; ops : k-abcdefghijkl : admin");

		assertThat(registry.authenticate("k-123456789012")).hasValueSatisfying(c -> {
			assertThat(c.clientId()).isEqualTo("claude");
			assertThat(c.scopes()).containsExactlyInAnyOrder("kb:read", "rules:read");
		});
		assertThat(registry.authenticate("k-abcdefghijkl")).hasValueSatisfying(c -> assertThat(c.scopes()).containsExactly("admin"));
		assertThat(registry.authenticate("wrong-key-000000")).isEmpty();
		assertThat(registry.authenticate(null)).isEmpty();
		assertThat(registry.authenticate("")).isEmpty();
	}

	@Test
	void emptyConfigurationMeansNoClients() {
		assertThat(ApiKeyRegistry.parse("").authenticate("anything-at-all")).isEmpty();
		assertThat(ApiKeyRegistry.parse(null).size()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = { "only-client", "c:short:kb:read", "c:k-123456789012:", "c:k-123456789012:unknown:scope",
			"a:k-123456789012:kb:read;b:k-123456789012:kb:read", "a:k-123456789012:kb:read;a:k-999999999999:kb:read" })
	void invalidConfigurationFailsFast(String value) {
		assertThatThrownBy(() -> ApiKeyRegistry.parse(value)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageNotContaining("k-123456789012");
	}

}
