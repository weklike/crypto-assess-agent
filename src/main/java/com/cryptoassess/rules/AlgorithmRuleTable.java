package com.cryptoassess.rules;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 算法合规规则表（src/main/resources/rules/algorithms.vN.yaml）。合规结论全部来自 YAML。
 */
public final class AlgorithmRuleTable {

	public static final Set<String> CATEGORIES = Set.of("block_cipher", "stream_cipher", "hash", "mac", "public_key",
			"signature", "key_exchange", "rng");

	/** 自由文本中的 ASCII 候选词，如 AES-256-GCM、SM3withSM2 */
	private static final Pattern ASCII_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_\\-/]*");

	private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\-_/]|(?i)with");

	/**
	 * @param securityBits 默认安全强度（比特）；写法里没有密钥位数、或位数不在 securityBitsByKey 中时使用，取保守值
	 * @param securityBitsByKey 密钥位数 → 安全强度
	 */
	public record Entry(String name, String category, List<String> aliases, AlgorithmStatus status, String basis,
			List<String> clauseRefs, Integer securityBits, Map<Integer, Integer> securityBitsByKey) {

		Integer securityBitsFor(Integer keyBits) {
			if (keyBits != null && this.securityBitsByKey.containsKey(keyBits)) {
				return this.securityBitsByKey.get(keyBits);
			}
			return this.securityBits;
		}

	}

	private final String version;

	private final boolean reviewed;

	private final Map<String, Entry> entries;

	private final AlgorithmNormalizer normalizer;

	private final List<String> nonAsciiAliases;

	private final Map<String, String> aliasToName;

	private AlgorithmRuleTable(String version, boolean reviewed, Map<String, Entry> entries) {
		this.version = version;
		this.reviewed = reviewed;
		this.entries = Map.copyOf(entries);
		this.aliasToName = new HashMap<>();
		entries.values().forEach(e -> e.aliases().forEach(a -> this.aliasToName.put(a, e.name())));
		this.normalizer = new AlgorithmNormalizer(this.aliasToName);
		this.nonAsciiAliases = this.aliasToName.keySet()
			.stream()
			.filter(a -> a.chars().anyMatch(c -> c > 127))
			.sorted(Comparator.comparingInt(String::length).reversed())
			.toList();
	}

	public String ruleVersion() {
		return "algorithms." + this.version;
	}

	/** 规则表是否已由仓库所有者核对；草稿状态下的结论要在输出中标注。 */
	public boolean reviewed() {
		return this.reviewed;
	}

	public AlgorithmNormalizer normalizer() {
		return this.normalizer;
	}

	public RuleCheck check(String raw) {
		AlgorithmNormalizer.Normalized n = this.normalizer.normalize(raw);
		Entry entry = this.entries.get(n.name());
		if (entry == null) {
			return new RuleCheck(n.input(), AlgorithmNormalizer.UNKNOWN, null, AlgorithmStatus.UNKNOWN, null, List.of(),
					ruleVersion(), null, null, null);
		}
		return new RuleCheck(n.input(), entry.name(), entry.category(), entry.status(), entry.basis(),
				entry.clauseRefs(), ruleVersion(), n.mode(), n.keyBits(), entry.securityBitsFor(n.keyBits()));
	}

	/**
	 * 用正则 + 别名词表从措施文本中抽取算法，按出现顺序、按规范名去重。认不出的词直接跳过，留给后续模型步骤。
	 */
	public List<RuleCheck> extract(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		record Hit(int position, RuleCheck check) {
		}
		List<Hit> hits = new ArrayList<>();
		Matcher token = ASCII_TOKEN.matcher(text);
		while (token.find()) {
			RuleCheck whole = check(token.group());
			if (whole.status() != AlgorithmStatus.UNKNOWN) {
				hits.add(new Hit(token.start(), whole));
				continue;
			}
			int offset = 0;
			for (String part : TOKEN_SPLIT.split(token.group())) {
				RuleCheck check = check(part);
				if (!part.isEmpty() && check.status() != AlgorithmStatus.UNKNOWN) {
					hits.add(new Hit(token.start() + offset, check));
				}
				offset += part.length() + 1;
			}
		}
		for (String alias : this.nonAsciiAliases) {
			for (int i = text.indexOf(alias); i >= 0; i = text.indexOf(alias, i + alias.length())) {
				hits.add(new Hit(i, check(alias)));
			}
		}
		hits.sort(Comparator.comparingInt(Hit::position));
		Map<String, RuleCheck> unique = new LinkedHashMap<>();
		hits.forEach(hit -> unique.putIfAbsent(hit.check().name(), hit.check()));
		return List.copyOf(unique.values());
	}

	public static AlgorithmRuleTable load(Resource resource) {
		Object root;
		try (InputStream in = resource.getInputStream()) {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
		}
		catch (IOException ex) {
			throw new RuleTableException("cannot read rule table " + resource.getDescription(), ex);
		}
		catch (YAMLException ex) {
			throw new RuleTableException("rule table " + resource.getDescription() + " is not valid YAML: "
					+ ex.getMessage(), ex);
		}
		String where = resource.getDescription();
		if (!(root instanceof Map<?, ?> map)) {
			throw new RuleTableException(where + ": root must be a mapping");
		}
		String version = requireText(map, "version", where);
		boolean reviewed = Boolean.TRUE.equals(map.get("reviewed"));
		if (!(map.get("algorithms") instanceof List<?> list) || list.isEmpty()) {
			throw new RuleTableException(where + ": algorithms must be a non-empty list");
		}
		Map<String, Entry> entries = new LinkedHashMap<>();
		Map<String, String> seenAliases = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			String at = where + ": algorithms[" + i + "]";
			if (!(list.get(i) instanceof Map<?, ?> item)) {
				throw new RuleTableException(at + " must be a mapping");
			}
			String name = requireText(item, "name", at);
			String category = requireText(item, "category", at);
			if (!CATEGORIES.contains(category)) {
				throw new RuleTableException(at + ": category " + category + " is not one of " + CATEGORIES);
			}
			String statusText = requireText(item, "status", at);
			AlgorithmStatus status;
			try {
				status = AlgorithmStatus.valueOf(statusText);
			}
			catch (IllegalArgumentException ex) {
				throw new RuleTableException(at + ": status " + statusText + " must be APPROVED, NOT_APPROVED or INSECURE");
			}
			if (status == AlgorithmStatus.UNKNOWN) {
				throw new RuleTableException(at + ": status UNKNOWN is reserved for unrecognized algorithms");
			}
			String basis = requireText(item, "basis", at);
			List<String> aliases = stringList(item.get("aliases"), at + ".aliases");
			if (aliases.isEmpty()) {
				throw new RuleTableException(at + ": aliases must not be empty");
			}
			Set<String> own = new LinkedHashSet<>(aliases);
			for (String alias : own) {
				String key = AlgorithmNormalizer.compact(alias);
				String previous = seenAliases.putIfAbsent(key, name);
				if (previous != null && !previous.equals(name)) {
					throw new RuleTableException(at + ": alias " + alias + " is already used by " + previous);
				}
			}
			if (entries.containsKey(name)) {
				throw new RuleTableException(at + ": duplicate algorithm name " + name);
			}
			entries.put(name, new Entry(name, category, List.copyOf(own), status, basis,
					stringList(item.get("clause_refs"), at + ".clause_refs"),
					optionalBits(item.get("security_bits"), at + ".security_bits"),
					bitsByKey(item.get("security_bits_by_key"), at + ".security_bits_by_key")));
		}
		return new AlgorithmRuleTable(version, reviewed, entries);
	}

	private static Integer optionalBits(Object value, String where) {
		if (value == null) {
			return null;
		}
		try {
			int bits = Integer.parseInt(String.valueOf(value).strip());
			if (bits < 0) {
				throw new RuleTableException(where + " must not be negative");
			}
			return bits;
		}
		catch (NumberFormatException ex) {
			throw new RuleTableException(where + " must be an integer", ex);
		}
	}

	private static Map<Integer, Integer> bitsByKey(Object value, String where) {
		if (value == null) {
			return Map.of();
		}
		if (!(value instanceof Map<?, ?> map)) {
			throw new RuleTableException(where + " must be a mapping of key bits to security bits");
		}
		Map<Integer, Integer> result = new HashMap<>();
		map.forEach((k, v) -> result.put(optionalBits(k, where + " key"), optionalBits(v, where + "." + k)));
		return Map.copyOf(result);
	}

	private static String requireText(Map<?, ?> map, String key, String where) {
		Object value = map.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new RuleTableException(where + ": " + key + " is required");
		}
		return String.valueOf(value).strip();
	}

	private static List<String> stringList(Object value, String where) {
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof List<?> list)) {
			throw new RuleTableException(where + " must be a list");
		}
		List<String> result = new ArrayList<>();
		for (Object item : list) {
			if (item == null || String.valueOf(item).isBlank()) {
				throw new RuleTableException(where + " contains an empty value");
			}
			result.add(String.valueOf(item).strip());
		}
		return result;
	}

}
