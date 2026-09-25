package com.cryptoassess.common.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final HealthService healthService;

	public HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	@GetMapping("/api/health")
	public ResponseEntity<HealthReport> health() {
		HealthReport report = this.healthService.check();
		HttpStatus status = (report.status() == CheckResult.Status.UP) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(status).body(report);
	}

}
