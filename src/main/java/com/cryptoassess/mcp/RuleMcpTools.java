package com.cryptoassess.mcp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cryptoassess.assessment.Judgment;
import com.cryptoassess.rules.AlgorithmRuleTable;
import com.cryptoassess.rules.OfficialScoreResult;
import com.cryptoassess.rules.OfficialScoringCalculator;
import com.cryptoassess.rules.OfficialScoringRules;
import com.cryptoassess.rules.RuleCheck;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 规则只读工具（scope rules:read）：确定性规则，不调用模型。
 */
@Component
public class RuleMcpTools {

	private static final int MAX_FINDINGS = 200;

	private final AlgorithmRuleTable ruleTable;

	private final OfficialScoringRules scoringRules;

	private final McpToolSupport support;

	public RuleMcpTools(AlgorithmRuleTable ruleTable, OfficialScoringRules scoringRules, McpToolSupport support) {
		this.ruleTable = ruleTable;
		this.scoringRules = scoringRules;
		this.support = support;
	}

	@McpTool(name = "check_algorithm",
			description = "按算法合规规则表检查一个密码算法写法（如 SM4-CBC、AES-256-GCM、RSA2048），返回规范化名称、类别、合规状态、安全强度（比特）和规则版本。认不出的返回 UNKNOWN。只读。",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public String checkAlgorithm(@McpToolParam(description = "算法名称或写法，最多 64 字符") String name) {
		return this.support.audited("check_algorithm", Map.of("name", String.valueOf(name)), () -> {
			RuleCheck check = this.ruleTable.check(McpToolSupport.require(name, "name", 64));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("input", check.input());
			m.put("name", check.name());
			m.put("category", check.category());
			m.put("status", check.status().name());
			m.put("basis", check.basis());
			m.put("mode", check.mode());
			m.put("keyBits", check.keyBits());
			m.put("securityBits", check.securityBits());
			m.put("ruleVersion", check.ruleVersion());
			m.put("rulesReviewed", this.ruleTable.reviewed());
			return m;
		});
	}

	@McpTool(name = "compute_score",
			description = "按《商用密码应用安全性评估量化评估规则（2023 版）》计算得分，返回总分、组、层面、测评单元、对象得分和计算明细。"
					+ "level 为等级 1-4；findings 最多 200 项，每项包含 object、layer、clause_ref，可选 clause_title；"
					+ "技术层面给 d、a、k（布尔），A 不满足时给 ra、K 不满足时给 rk；管理层面给 judgment（符合/部分符合/不符合）；"
					+ "judgment 为“不适用”的项不参与计算。某组全部不适用时不出总分。只读。",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public String computeScore(@McpToolParam(description = "等级 1-4") Integer level,
			@McpToolParam(description = "差距项列表") List<Map<String, Object>> findings) {
		Map<String, Object> args = new LinkedHashMap<>();
		args.put("level", level);
		args.put("findings", (findings == null) ? List.of() : findings);
		return this.support.audited("compute_score", args, () -> {
			if (level == null || level < 1 || level > 4) {
				throw new IllegalArgumentException("level must be 1-4");
			}
			if (findings == null || findings.isEmpty() || findings.size() > MAX_FINDINGS) {
				throw new IllegalArgumentException("findings must contain 1-" + MAX_FINDINGS + " items");
			}
			List<OfficialScoringCalculator.Item> items = new ArrayList<>();
			for (Map<String, Object> f : findings) {
				items.add(item(f));
			}
			OfficialScoreResult result = OfficialScoringCalculator.calculate(this.scoringRules, level, items);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("total", result.total());
			m.put("totalNote", result.totalNote());
			m.put("groups", result.groups());
			m.put("layers", result.layers());
			m.put("units", result.units());
			m.put("objects", result.objects());
			m.put("detail", result.detail().stream().limit(60).toList());
			m.put("ruleVersion", result.ruleVersion());
			m.put("rulesReviewed", this.scoringRules.reviewed());
			return m;
		});
	}

	private OfficialScoringCalculator.Item item(Map<String, Object> f) {
		String object = McpToolSupport.require(str(f.get("object")), "object", 64);
		String layer = McpToolSupport.require(str(f.get("layer")), "layer", 16);
		String ref = McpToolSupport.require(str(f.get("clause_ref")), "clause_ref", 128);
		String title = (f.get("clause_title") == null) ? null
				: McpToolSupport.require(str(f.get("clause_title")), "clause_title", 128);
		String judgment = (f.get("judgment") == null) ? null : McpToolSupport.require(str(f.get("judgment")), "judgment", 8);
		if (judgment != null && !Judgment.VALUES.contains(judgment)) {
			throw new IllegalArgumentException("judgment must be one of " + Judgment.VALUES);
		}
		if ("不适用".equals(judgment)) {
			return OfficialScoringCalculator.Item.notApplicable(object, object, layer, ref, title);
		}
		boolean dak = this.scoringRules.groupOf(layer).map(OfficialScoringRules.Group::dak).orElse(false);
		if (dak) {
			return OfficialScoringCalculator.Item.technical(object, object, layer, ref, title, bool(f, "d"), bool(f, "a"),
					bool(f, "k"), decimal(f, "ra"), decimal(f, "rk"));
		}
		return OfficialScoringCalculator.Item.management(object, object, layer, ref, title, judgment);
	}

	private static Boolean bool(Map<String, Object> f, String key) {
		Object value = f.get(key);
		if (value == null || value instanceof Boolean) {
			return (Boolean) value;
		}
		throw new IllegalArgumentException(key + " must be true or false");
	}

	private static BigDecimal decimal(Map<String, Object> f, String key) {
		Object value = f.get(key);
		if (value == null) {
			return null;
		}
		try {
			return new BigDecimal(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException(key + " must be a number");
		}
	}

	private static String str(Object value) {
		return (value == null) ? null : String.valueOf(value);
	}

}
