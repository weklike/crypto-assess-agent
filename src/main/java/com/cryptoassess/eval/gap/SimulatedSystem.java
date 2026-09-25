package com.cryptoassess.eval.gap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cryptoassess.assessment.Judgment;
import com.cryptoassess.knowledge.format.NormalizedFormat;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 模拟被测系统（eval/datasets/systems/*.vN.yaml）。预期差距项只由仓库所有者填写，这里只读。
 */
public record SimulatedSystem(String id, String name, int level, String scenario, String description,
		List<SimObject> objects, List<Expected> expected, String file) {

	public record SimObject(String key, String layer, String name, String description, Map<String, Object> measures) {
	}

	public record Expected(String object, String clauseRef, String judgment) {
	}

	@SuppressWarnings("unchecked")
	public static SimulatedSystem load(Path file) throws IOException {
		Map<String, Object> root;
		try (InputStream in = Files.newInputStream(file)) {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
		}
		String where = file.getFileName().toString();
		String id = required(root, "id", where);
		int level = Integer.parseInt(required(root, "level", where));
		if (level < 1 || level > 4) {
			throw new IllegalArgumentException(where + ": level must be 1-4");
		}
		List<SimObject> objects = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (Map<String, Object> o : (List<Map<String, Object>>) root.getOrDefault("objects", List.of())) {
			String key = required(o, "key", where + " objects");
			String layer = required(o, "layer", where + " objects." + key);
			if (!NormalizedFormat.LAYERS.contains(layer)) {
				throw new IllegalArgumentException(where + ": objects." + key + ".layer " + layer + " is not a 安全层面");
			}
			if (!keys.add(key)) {
				throw new IllegalArgumentException(where + ": duplicate object key " + key);
			}
			objects.add(new SimObject(key, layer, required(o, "name", where + " objects." + key),
					(String) o.get("description"), (Map<String, Object>) o.getOrDefault("measures", Map.of())));
		}
		if (objects.isEmpty()) {
			throw new IllegalArgumentException(where + ": objects must not be empty");
		}
		List<Expected> expected = new ArrayList<>();
		for (Map<String, Object> e : (List<Map<String, Object>>) root.getOrDefault("expected", List.of())) {
			String object = required(e, "object", where + " expected");
			String judgment = required(e, "judgment", where + " expected");
			if (!keys.contains(object)) {
				throw new IllegalArgumentException(where + ": expected refers to unknown object " + object);
			}
			if (!Judgment.VALUES.contains(judgment)) {
				throw new IllegalArgumentException(where + ": expected judgment " + judgment + " is invalid");
			}
			expected.add(new Expected(object, required(e, "clause_ref", where + " expected"), judgment));
		}
		return new SimulatedSystem(id, required(root, "name", where), level,
				String.valueOf(root.getOrDefault("scenario", "")), String.valueOf(root.getOrDefault("description", "")),
				List.copyOf(objects), List.copyOf(expected), where);
	}

	private static String required(Map<String, Object> map, String key, String where) {
		Object value = map.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException(where + ": " + key + " is required");
		}
		return String.valueOf(value).strip();
	}

}
