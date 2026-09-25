package com.cryptoassess.rules;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cryptoassess.knowledge.format.NormalizedFormat;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 按《商用密码应用安全性评估量化评估规则（2023 版）》组织的评分规则（rules/scoring.v2.yaml，format: official-2023）。
 * 所有权重、分值、修正参数和舍入都来自 YAML。
 *
 * @param version 带前缀的版本号，如 scoring.v2
 */
public record OfficialScoringRules(String version, boolean reviewed, int intermediateScale, int totalScale,
		RoundingMode roundingMode, String notApplicable, Map<String, BigDecimal> managementScores, List<RaStep> ra,
		Map<Integer, RkRule> rk, List<String> unscoredTitles, Set<String> unscoredClauseRefs, List<Group> groups,
		Map<String, BigDecimal> layers, List<Unit> units) {

	public static final String FORMAT = "official-2023";

	/** 表 2：安全强度 ≥ minBits 时 Ra = value。 */
	public record RaStep(int minBits, BigDecimal value) {
	}

	/** 表 3：使用 moduleLevel 级密码模块且满足其他密钥管理要求时取 value，否则取 defaultValue。 */
	public record RkRule(BigDecimal defaultValue, Integer moduleLevel, BigDecimal value) {
	}

	/**
	 * @param scoring dak：技术要求，按 D/A/K 给测评对象计分；judgment：管理要求，按判定给测评单元计分
	 */
	public record Group(String name, BigDecimal weight, String scoring, List<String> layers) {

		public boolean dak() {
			return "dak".equals(this.scoring);
		}

	}

	/**
	 * @param weights 等级 → 单元权重；缺少的等级表示该等级不评价（原表中的“/”）
	 */
	public record Unit(String layer, String name, List<String> titles, Map<Integer, BigDecimal> weights,
			List<String> clauseRefs) {

		public BigDecimal weightFor(int level) {
			return this.weights.get(level);
		}

		public String key() {
			return this.layer + "|" + this.name;
		}

		boolean matchesTitle(String title) {
			String t = compact(title);
			return compact(this.name).equals(t) || this.titles.stream().anyMatch(x -> compact(x).equals(t));
		}

	}

	public BigDecimal layerWeight(String layer) {
		return this.layers.get(layer);
	}

	public Unit unit(String layer, String name) {
		return this.units.stream()
			.filter(u -> u.layer().equals(layer) && u.name().equals(name))
			.findFirst()
			.orElse(null);
	}

	public Optional<Group> groupOf(String layer) {
		return this.groups.stream().filter(g -> g.layers().contains(layer)).findFirst();
	}

	/** 先查显式映射（clause_refs），再按同一层面内的标题完全一致匹配。 */
	public Optional<Unit> findUnit(String layer, String clauseRef, String clauseTitle) {
		for (Unit unit : this.units) {
			if (unit.clauseRefs().stream().anyMatch(ref -> within(clauseRef, ref))) {
				return Optional.of(unit);
			}
		}
		if (clauseTitle == null || clauseTitle.isBlank()) {
			return Optional.empty();
		}
		return this.units.stream().filter(u -> u.layer().equals(layer) && u.matchesTitle(clauseTitle)).findFirst();
	}

	/** 通用要求与“密码服务”“密码产品”指标不单独评价。 */
	public boolean isUnscored(String clauseRef, String clauseTitle) {
		if (this.unscoredClauseRefs.stream().anyMatch(ref -> within(clauseRef, ref))) {
			return true;
		}
		return clauseTitle != null && this.unscoredTitles.stream().anyMatch(clauseTitle::contains);
	}

	/**
	 * 条款是映射条款本身或其下级条款。GB/T 43206 的测评单元（如 6.1.2）下分测评指标、测评对象、测评实施
	 * （6.1.2.1–6.1.2.3），规范化时哪一级标为“要求”取决于切分方式，映射只写单元一级。
	 */
	static boolean within(String clauseRef, String mappedRef) {
		return clauseRef != null && (clauseRef.equals(mappedRef) || clauseRef.startsWith(mappedRef + "."));
	}

	public BigDecimal ra(int securityBits) {
		for (RaStep step : this.ra) {
			if (securityBits >= step.minBits()) {
				return step.value();
			}
		}
		throw new IllegalArgumentException("no Ra step for " + securityBits + " bits");
	}

	/**
	 * @param moduleLevel 所用密码模块的安全等级，未知时为 null
	 * @param otherRequirementsMet 是否满足 GM/T 0115—2021“5.5 密钥管理安全性”的其他要求
	 */
	public BigDecimal rk(int level, Integer moduleLevel, boolean otherRequirementsMet) {
		RkRule rule = this.rk.get(level);
		if (rule == null) {
			throw new IllegalArgumentException("no Rk rule for level " + level);
		}
		if (rule.moduleLevel() != null && rule.moduleLevel().equals(moduleLevel) && otherRequirementsMet) {
			return rule.value();
		}
		return rule.defaultValue();
	}

	public BigDecimal managementScore(String judgment) {
		BigDecimal score = this.managementScores.get(judgment);
		if (score == null) {
			throw new IllegalArgumentException("judgment " + judgment + " has no management score in " + this.version);
		}
		return score.setScale(this.intermediateScale, this.roundingMode);
	}

	static String compact(String text) {
		return (text == null) ? "" : text.replaceAll("[\\s　]+", "");
	}

	@SuppressWarnings("unchecked")
	public static OfficialScoringRules load(Resource resource) {
		String where = resource.getDescription();
		Object root;
		try (InputStream in = resource.getInputStream()) {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
		}
		catch (IOException ex) {
			throw new RuleTableException("cannot read scoring rules " + where, ex);
		}
		catch (YAMLException ex) {
			throw new RuleTableException(where + " is not valid YAML: " + ex.getMessage(), ex);
		}
		if (!(root instanceof Map<?, ?> map)) {
			throw new RuleTableException(where + ": root must be a mapping");
		}
		if (!FORMAT.equals(String.valueOf(map.get("format")))) {
			throw new RuleTableException(where + ": format must be " + FORMAT);
		}
		String version = text(map.get("version"), where + ": version");
		Map<?, ?> rounding = mapping(map.get("rounding"), where + ": rounding");
		int intermediate;
		int total;
		RoundingMode mode;
		try {
			intermediate = Integer.parseInt(String.valueOf(rounding.get("intermediate_scale")));
			total = Integer.parseInt(String.valueOf(rounding.get("total_scale")));
			mode = RoundingMode.valueOf(String.valueOf(rounding.get("mode")));
		}
		catch (IllegalArgumentException ex) {
			throw new RuleTableException(where + ": rounding must be {intermediate_scale, total_scale, mode}", ex);
		}
		Map<String, BigDecimal> management = new LinkedHashMap<>();
		mapping(map.get("management_scores"), where + ": management_scores")
			.forEach((k, v) -> management.put(String.valueOf(k), unitInterval(v, where + ": management_scores." + k)));

		List<RaStep> ra = new ArrayList<>();
		for (Object o : list(map.get("ra"), where + ": ra")) {
			Map<?, ?> step = mapping(o, where + ": ra[]");
			ra.add(new RaStep(Integer.parseInt(String.valueOf(step.get("min_bits"))),
					number(step.get("value"), where + ": ra.value")));
		}
		ra.sort(Comparator.comparingInt(RaStep::minBits).reversed());
		if (ra.isEmpty() || ra.get(ra.size() - 1).minBits() != 0) {
			throw new RuleTableException(where + ": ra must contain a step with min_bits 0");
		}

		Map<Integer, RkRule> rk = new HashMap<>();
		mapping(map.get("rk"), where + ": rk").forEach((k, v) -> {
			Map<?, ?> rule = mapping(v, where + ": rk." + k);
			Object moduleLevel = rule.get("module_level");
			rk.put(Integer.valueOf(String.valueOf(k)), new RkRule(number(rule.get("default"), where + ": rk." + k + ".default"),
					(moduleLevel == null) ? null : Integer.valueOf(String.valueOf(moduleLevel)),
					(moduleLevel == null) ? null : number(rule.get("value"), where + ": rk." + k + ".value")));
		});

		Map<String, BigDecimal> layers = new LinkedHashMap<>();
		mapping(map.get("layers"), where + ": layers").forEach((k, v) -> {
			String layer = String.valueOf(k);
			if (!NormalizedFormat.LAYERS.contains(layer)) {
				throw new RuleTableException(where + ": layers." + layer + " is not a 安全层面");
			}
			layers.put(layer, number(v, where + ": layers." + layer));
		});

		List<Group> groups = new ArrayList<>();
		Set<String> grouped = new HashSet<>();
		for (Object o : list(map.get("groups"), where + ": groups")) {
			Map<?, ?> g = mapping(o, where + ": groups[]");
			String scoring = String.valueOf(g.get("scoring"));
			if (!scoring.equals("dak") && !scoring.equals("judgment")) {
				throw new RuleTableException(where + ": groups.scoring must be dak or judgment");
			}
			List<String> groupLayers = list(g.get("layers"), where + ": groups.layers").stream().map(String::valueOf).toList();
			for (String layer : groupLayers) {
				if (!layers.containsKey(layer) || !grouped.add(layer)) {
					throw new RuleTableException(where + ": group layer " + layer + " is unknown or listed twice");
				}
			}
			groups.add(new Group(text(g.get("name"), where + ": groups.name"), number(g.get("weight"), where + ": groups.weight"),
					scoring, groupLayers));
		}
		if (!grouped.equals(layers.keySet())) {
			throw new RuleTableException(where + ": every layer must belong to exactly one group");
		}

		List<Unit> units = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (Object o : list(map.get("units"), where + ": units")) {
			Map<?, ?> u = mapping(o, where + ": units[]");
			String layer = String.valueOf(u.get("layer"));
			String name = text(u.get("name"), where + ": units.name");
			if (!layers.containsKey(layer)) {
				throw new RuleTableException(where + ": unit " + name + " has unknown layer " + layer);
			}
			if (!keys.add(layer + "|" + name)) {
				throw new RuleTableException(where + ": duplicate unit " + layer + "|" + name);
			}
			Map<Integer, BigDecimal> weights = new HashMap<>();
			mapping(u.get("weights"), where + ": units." + name + ".weights").forEach((k, v) -> {
				int level = Integer.parseInt(String.valueOf(k));
				if (level < 1 || level > 4) {
					throw new RuleTableException(where + ": unit " + name + " has invalid level " + level);
				}
				weights.put(level, number(v, where + ": units." + name + ".weights." + k));
			});
			units.add(new Unit(layer, name, strings(u.get("titles")), Map.copyOf(weights), strings(u.get("clause_refs"))));
		}
		return new OfficialScoringRules("scoring." + version, Boolean.TRUE.equals(map.get("reviewed")), intermediate,
				total, mode, text(map.get("not_applicable"), where + ": not_applicable"), Map.copyOf(management),
				List.copyOf(ra), Map.copyOf(rk), strings(map.get("unscored_titles")),
				Set.copyOf(strings(map.get("unscored_clause_refs"))), List.copyOf(groups),
				java.util.Collections.unmodifiableMap(layers), List.copyOf(units));
	}

	private static Map<?, ?> mapping(Object value, String where) {
		if (!(value instanceof Map<?, ?> map)) {
			throw new RuleTableException(where + " must be a mapping");
		}
		return map;
	}

	private static List<?> list(Object value, String where) {
		if (!(value instanceof List<?> list)) {
			throw new RuleTableException(where + " must be a list");
		}
		return list;
	}

	private static List<String> strings(Object value) {
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof List<?> list)) {
			throw new RuleTableException("expected a list but got " + value);
		}
		return list.stream().map(String::valueOf).toList();
	}

	private static String text(Object value, String where) {
		if (value == null || String.valueOf(value).isBlank()) {
			throw new RuleTableException(where + " is required");
		}
		return String.valueOf(value).strip();
	}

	private static BigDecimal number(Object value, String where) {
		if (value == null) {
			throw new RuleTableException(where + " is required");
		}
		try {
			BigDecimal number = new BigDecimal(String.valueOf(value));
			if (number.signum() < 0) {
				throw new RuleTableException(where + " must not be negative");
			}
			return number;
		}
		catch (NumberFormatException ex) {
			throw new RuleTableException(where + " must be a number", ex);
		}
	}

	private static BigDecimal unitInterval(Object value, String where) {
		BigDecimal n = number(value, where);
		if (n.compareTo(BigDecimal.ONE) > 0) {
			throw new RuleTableException(where + " must be between 0 and 1");
		}
		return n;
	}

}
