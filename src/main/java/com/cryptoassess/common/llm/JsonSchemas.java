package com.cryptoassess.common.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 从 classpath:schemas/ 读取 JSON Schema（draft 2020-12）并校验模型输出。
 */
public final class JsonSchemas {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final SchemaRegistry REGISTRY = SchemaRegistry
		.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

	private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

	private static final Map<String, String> TEXT = new ConcurrentHashMap<>();

	private JsonSchemas() {
	}

	public static String text(String name) {
		return TEXT.computeIfAbsent(name, n -> {
			ClassPathResource resource = new ClassPathResource("schemas/" + n + ".json");
			try (InputStream in = resource.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new IllegalStateException("JSON schema not found: " + n, ex);
			}
		});
	}

	/** 返回校验错误信息，空列表表示通过。 */
	public static List<String> validate(String name, JsonNode instance) {
		Schema schema = CACHE.computeIfAbsent(name, n -> REGISTRY.getSchema(JSON.readTree(text(n))));
		return schema.validate(instance).stream().map(Error::getMessage).toList();
	}

}
