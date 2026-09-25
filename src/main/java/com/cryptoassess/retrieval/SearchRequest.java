package com.cryptoassess.retrieval;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param mode 为空时用 hybrid
 * @param k 返回条数，为空时 10，最多 50
 */
public record SearchRequest(@NotBlank @Size(max = 500) String query, SearchMode mode, @Min(1) @Max(50) Integer k,
		@Size(max = 16) String layer, @Min(1) @Max(4) Integer level) {

	public static final int DEFAULT_K = 10;

	public SearchMode effectiveMode() {
		return (this.mode == null) ? SearchMode.HYBRID : this.mode;
	}

	public int effectiveK() {
		return (this.k == null) ? DEFAULT_K : this.k;
	}

	public SearchFilter filter() {
		return new SearchFilter(blankToNull(this.layer), this.level);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

}
