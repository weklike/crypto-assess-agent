package com.cryptoassess.assessment;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AssessObject(Long id, Long projectId, String layer, String name, String description,
		@JsonIgnore String measuresJson, Instant createdAt, Instant updatedAt) {

	public CryptoMeasures measures() {
		return CryptoMeasures.parse(this.measuresJson);
	}

}
