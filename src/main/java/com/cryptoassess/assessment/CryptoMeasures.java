package com.cryptoassess.assessment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cryptoassess.common.error.AppException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 测评对象的密码措施（assess_object.measures_json），按用途分组。
 */
public record CryptoMeasures(@Valid @Size(max = 20) List<Measure> transport,
		@Valid @Size(max = 20) List<Measure> storage, @Valid @Size(max = 20) List<Measure> auth,
		@Valid @Size(max = 20) @JsonProperty("key_mgmt") List<Measure> keyMgmt,
		@Valid @Size(max = 20) List<Measure> other) {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.build();

	public CryptoMeasures {
		transport = (transport == null) ? List.of() : List.copyOf(transport);
		storage = (storage == null) ? List.of() : List.copyOf(storage);
		auth = (auth == null) ? List.of() : List.copyOf(auth);
		keyMgmt = (keyMgmt == null) ? List.of() : List.copyOf(keyMgmt);
		other = (other == null) ? List.of() : List.copyOf(other);
	}

	public static final CryptoMeasures EMPTY = new CryptoMeasures(null, null, null, null, null);

	/** 按类别列出（类别名与 JSON 键一致），规则检查和判定都按这个顺序遍历。 */
	@JsonIgnore
	public Map<String, List<Measure>> byCategory() {
		Map<String, List<Measure>> map = new LinkedHashMap<>();
		map.put("transport", this.transport);
		map.put("storage", this.storage);
		map.put("auth", this.auth);
		map.put("key_mgmt", this.keyMgmt);
		map.put("other", this.other);
		return map;
	}

	@JsonIgnore
	public List<Measure> all() {
		List<Measure> all = new ArrayList<>();
		byCategory().values().forEach(all::addAll);
		return all;
	}

	/** 每项措施至少有一个字段非空；失败时报出位置，如 transport[0]。 */
	public CryptoMeasures validate() {
		byCategory().forEach((category, measures) -> {
			for (int i = 0; i < measures.size(); i++) {
				if (measures.get(i) == null || measures.get(i).isBlank()) {
					throw AppException.invalid("measures." + category + "[" + i + "] must have at least one field");
				}
			}
		});
		return this;
	}

	public CryptoMeasures normalized() {
		return new CryptoMeasures(norm(this.transport), norm(this.storage), norm(this.auth), norm(this.keyMgmt),
				norm(this.other));
	}

	public String toJson() {
		return JSON.writeValueAsString(this);
	}

	public static CryptoMeasures parse(String json) {
		if (json == null || json.isBlank()) {
			return EMPTY;
		}
		try {
			return JSON.readValue(json, CryptoMeasures.class).validate().normalized();
		}
		catch (JacksonException ex) {
			throw AppException.invalid("invalid measures: " + ex.getOriginalMessage());
		}
	}

	private static List<Measure> norm(List<Measure> measures) {
		return measures.stream().map(Measure::normalized).toList();
	}

}
