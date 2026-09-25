package com.cryptoassess.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class HealthServiceTest {

	private static final Duration TIMEOUT = Duration.ofMillis(300);

	@Test
	void allChecksUpMeansOverallUp() {
		HealthService service = new HealthService(
				List.of(check("mysql", () -> CheckResult.up("ok")), check("redis", () -> CheckResult.up("PONG"))),
				new HealthProperties(TIMEOUT));

		HealthReport report = service.check();

		assertThat(report.status()).isEqualTo(CheckResult.Status.UP);
		assertThat(report.components()).containsOnlyKeys("mysql", "redis");
	}

	@Test
	void oneDownCheckMakesOverallDownAndKeepsOthers() {
		HealthService service = new HealthService(List.of(check("mysql", () -> CheckResult.up("ok")),
				check("tei", () -> CheckResult.down("connection refused"))), new HealthProperties(TIMEOUT));

		HealthReport report = service.check();

		assertThat(report.status()).isEqualTo(CheckResult.Status.DOWN);
		assertThat(report.components().get("mysql").status()).isEqualTo(CheckResult.Status.UP);
		assertThat(report.components().get("tei").detail()).isEqualTo("connection refused");
	}

	@Test
	void throwingCheckIsReportedAsDown() {
		HealthService service = new HealthService(List.of(check("es", () -> {
			throw new IllegalStateException("boom");
		})), new HealthProperties(TIMEOUT));

		CheckResult es = service.check().components().get("es");

		assertThat(es.status()).isEqualTo(CheckResult.Status.DOWN);
		assertThat(es.detail()).contains("IllegalStateException").contains("boom");
	}

	@Test
	void slowCheckTimesOutAsDown() {
		HealthService service = new HealthService(List.of(check("ollama", () -> {
			try {
				Thread.sleep(5_000);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return CheckResult.up("late");
		})), new HealthProperties(TIMEOUT));

		long start = System.nanoTime();
		CheckResult ollama = service.check().components().get("ollama");

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
		assertThat(ollama.status()).isEqualTo(CheckResult.Status.DOWN);
		assertThat(ollama.detail()).contains("timed out");
	}

	private static DependencyCheck check(String name, Supplier<CheckResult> result) {
		return new DependencyCheck() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public CheckResult check() {
				return result.get();
			}
		};
	}

}
