package com.cryptoassess.common.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

	private static final Logger log = LoggerFactory.getLogger(HealthService.class);

	private final List<DependencyCheck> checks;

	private final HealthProperties properties;

	public HealthService(List<DependencyCheck> checks, HealthProperties properties) {
		this.checks = checks;
		this.properties = properties;
	}

	/**
	 * 并行执行全部检查；每项单独限时，慢的依赖不会拖住整个接口。
	 */
	public HealthReport check() {
		Map<String, CheckResult> results = new LinkedHashMap<>();
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Map<String, Future<CheckResult>> futures = new LinkedHashMap<>();
			for (DependencyCheck check : this.checks) {
				futures.put(check.name(), executor.submit(check::check));
			}
			long deadline = System.nanoTime() + this.properties.timeout().toNanos();
			futures.forEach((name, future) -> results.put(name, await(name, future, deadline)));
			// 超时的任务要中断，否则 try-with-resources 的 close() 会一直等它结束
			futures.values().forEach(future -> future.cancel(true));
		}
		return HealthReport.of(results, Instant.now());
	}

	private CheckResult await(String name, Future<CheckResult> future, long deadline) {
		try {
			return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException ex) {
			log.warn("Health check {} timed out after {}", name, this.properties.timeout());
			return CheckResult.down("timed out after " + this.properties.timeout().toMillis() + " ms");
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			log.warn("Health check {} failed: {}", name, cause.toString());
			return CheckResult.down(cause.getClass().getSimpleName() + ": " + cause.getMessage());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return CheckResult.down("interrupted");
		}
	}

}
