package com.cryptoassess.qa;

import com.cryptoassess.retrieval.SearchMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param mode 检索模式，为空时用 app.qa.mode（默认 hybrid_rerank）
 */
public record QaRequest(@NotBlank @Size(max = 1000) String question, SearchMode mode, @Size(max = 16) String layer,
		@Min(1) @Max(4) Integer level) {

}
