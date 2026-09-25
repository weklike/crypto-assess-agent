package com.cryptoassess.assessment;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 评估接口的请求体。
 */
public final class AssessmentRequests {

	private AssessmentRequests() {
	}

	public record CreateAssessment(@NotBlank @Size(max = 128) String name, @NotBlank @Size(max = 128) String systemName,
			@NotNull @Min(1) @Max(4) Integer level) {
	}

	/**
	 * @param layer 安全层面，取值见 NormalizedFormat.LAYERS
	 * @param measures 原样接收，由 {@link CryptoMeasures#parse} 严格解析（未知类别直接报错，全局的 Jackson 配置会忽略未知字段）
	 */
	public record AddObject(@NotBlank @Size(max = 32) String layer, @NotBlank @Size(max = 128) String name,
			@Size(max = 2000) String description, @NotNull JsonNode measures) {
	}

	/**
	 * 人工复核。技术层面可以直接修改 D/A/K 与 Ra、Rk（判定由维度推出）；也可以只给判定：
	 * 符合 = D、A、K 都满足，不符合 = D 不满足，不适用；技术层面的“部分符合”必须给出 D/A/K。
	 *
	 * @param judgment 修改后的判定，为空表示不改
	 * @param note 复核备注
	 * @param reviewed 是否标记为已确认
	 */
	public record ReviewFinding(@Size(max = 8) String judgment, @Size(max = 1000) String note, Boolean reviewed,
			Boolean d, Boolean a, Boolean k, @DecimalMin("0") @DecimalMax("2") java.math.BigDecimal ra,
			@DecimalMin("0") @DecimalMax("2") java.math.BigDecimal rk) {

		public ReviewFinding(String judgment, String note, Boolean reviewed) {
			this(judgment, note, reviewed, null, null, null, null, null);
		}

		boolean changesDimensions() {
			return this.d != null || this.a != null || this.k != null || this.ra != null || this.rk != null;
		}

	}

}
