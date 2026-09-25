package com.cryptoassess.common.health;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record HealthReport(CheckResult.Status status, Map<String, CheckResult> components, Instant checkedAt) {

	public static HealthReport of(Map<String, CheckResult> components, Instant checkedAt) {
		boolean allUp = components.values().stream().allMatch(CheckResult::isUp);
		return new HealthReport(allUp ? CheckResult.Status.UP : CheckResult.Status.DOWN, Collections.unmodifiableMap(new LinkedHashMap<>(components)),
				checkedAt);
	}

}
