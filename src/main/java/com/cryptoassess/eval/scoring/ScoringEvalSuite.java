package com.cryptoassess.eval.scoring;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cryptoassess.eval.EvalContext;
import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.EvalOutcome;
import com.cryptoassess.eval.EvalSuite;
import com.cryptoassess.eval.report.MarkdownTable;
import com.cryptoassess.rules.OfficialScoreResult;
import com.cryptoassess.rules.OfficialScoringCalculator;
import com.cryptoassess.rules.OfficialScoringRules;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 评分一致性评测：用当前评分规则计算 golden 用例，逐项比对总分、层面分、测评单元分、对象分。目标 100% 一致。
 * golden 用例由仓库所有者按规则示例手算（默认 src/test/resources/scoring_golden.&lt;版本&gt;.yaml），格式：
 * 每个用例有 level 和 findings；技术层面的 finding 给 d/a/k（及 ra/rk），管理层面给 judgment，
 * judgment 为“不适用”表示不参与计算。期望总分写 null 表示“不出总分”，此时可用 total_note 写原因中应出现的文字。
 */
@Component
public class ScoringEvalSuite implements EvalSuite {

	private final OfficialScoringRules rules;

	public ScoringEvalSuite(OfficialScoringRules rules) {
		this.rules = rules;
	}

	@Override
	public String name() {
		return "scoring";
	}

	@Override
	@SuppressWarnings("unchecked")
	public EvalOutcome run(EvalContext context) throws IOException {
		Path golden = (context.datasetPath() != null) ? context.datasetPath()
				: Path.of("src/test/resources", "scoring_golden." + context.datasetVersion() + ".yaml");
		String text = Files.readString(golden, StandardCharsets.UTF_8);
		Map<String, Object> root;
		try (InputStream in = Files.newInputStream(golden)) {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
		}
		List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");
		List<Map<String, Object>> lines = new ArrayList<>();
		List<String> mismatches = new ArrayList<>();
		int agreed = 0;
		for (Map<String, Object> c : cases) {
			List<String> diffs = new ArrayList<>();
			try {
				List<OfficialScoringCalculator.Item> items = new ArrayList<>();
				for (Map<String, Object> f : (List<Map<String, Object>>) c.get("findings")) {
					items.add(item(f));
				}
				OfficialScoreResult result = OfficialScoringCalculator.calculate(this.rules,
						((Number) c.getOrDefault("level", 3)).intValue(), items);
				if (c.containsKey("total")) {
					compare("total", c.get("total"), result.total(), diffs);
				}
				if (c.get("total_note") != null && (result.totalNote() == null
						|| !result.totalNote().contains(String.valueOf(c.get("total_note"))))) {
					diffs.add("total_note 期望包含 " + c.get("total_note") + " 实际 " + result.totalNote());
				}
				((Map<String, Object>) c.getOrDefault("layers", Map.of()))
					.forEach((k, v) -> compare("layer " + k, v, result.layers().get(k), diffs));
				((Map<String, Object>) c.getOrDefault("units", Map.of()))
					.forEach((k, v) -> compare("unit " + k, v, result.units().get(k), diffs));
				((Map<String, Object>) c.getOrDefault("objects", Map.of()))
					.forEach((k, v) -> compare("object " + k, v, result.objects().get(k), diffs));
				if (c.get("error") != null) {
					diffs.add("期望报错（" + c.get("error") + "），实际正常算出");
				}
			}
			catch (IllegalArgumentException ex) {
				// 期望报错的用例写 error: 报错中应出现的文字
				if (c.get("error") == null || !String.valueOf(ex.getMessage()).contains(String.valueOf(c.get("error")))) {
					diffs.add("计算报错：" + ex.getMessage());
				}
			}
			boolean ok = diffs.isEmpty();
			agreed += ok ? 1 : 0;
			if (!ok) {
				mismatches.add(c.get("id") + ": " + String.join("; ", diffs));
			}
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("id", c.get("id"));
			line.put("agreed", ok);
			line.put("diffs", diffs);
			lines.add(line);
		}
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("suite", name());
		config.put("gitCommit", context.gitCommit());
		config.put("datasetVersion", context.datasetVersion());
		config.put("goldenFile", golden.toString());
		config.put("datasetSha256", EvalFiles.sha256(text));
		config.put("ruleVersion", this.rules.version());
		config.put("rulesReviewed", this.rules.reviewed());
		config.put("startedAt", context.startedAt().toString());
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("cases", cases.size());
		metrics.put("agreed", agreed);
		metrics.put("agreement", cases.isEmpty() ? 0.0 : (double) agreed / cases.size());
		metrics.put("mismatches", mismatches);

		StringBuilder md = new StringBuilder("# 评分一致性评测报告\n\n## 运行信息\n\n");
		MarkdownTable info = new MarkdownTable("项", "值");
		config.forEach(info::row);
		md.append(info.render()).append("\n## 结果\n\n一致 ").append(agreed).append(" / ").append(cases.size()).append("\n\n");
		if (!mismatches.isEmpty()) {
			MarkdownTable table = new MarkdownTable("不一致的用例");
			mismatches.forEach(table::row);
			md.append(table.render());
		}
		EvalFiles.writeCases(context.outputDir(), lines);
		EvalFiles.writeMetrics(context.outputDir(), config, metrics);
		EvalFiles.writeReport(context.outputDir(), md.toString());
		return new EvalOutcome(config, metrics);
	}

	private static OfficialScoringCalculator.Item item(Map<String, Object> f) {
		String object = String.valueOf(f.get("object"));
		String layer = String.valueOf(f.get("layer"));
		String ref = String.valueOf(f.get("clause_ref"));
		String title = (f.get("clause_title") == null) ? null : String.valueOf(f.get("clause_title"));
		String judgment = (f.get("judgment") == null) ? null : String.valueOf(f.get("judgment"));
		if ("不适用".equals(judgment)) {
			return OfficialScoringCalculator.Item.notApplicable(object, object, layer, ref, title);
		}
		if (f.containsKey("d")) {
			return OfficialScoringCalculator.Item.technical(object, object, layer, ref, title, bool(f.get("d")),
					bool(f.get("a")), bool(f.get("k")), decimal(f.get("ra")), decimal(f.get("rk")));
		}
		return OfficialScoringCalculator.Item.management(object, object, layer, ref, title, judgment);
	}

	private static Boolean bool(Object value) {
		return (value == null) ? null : (Boolean) value;
	}

	private static BigDecimal decimal(Object value) {
		return (value == null) ? null : new BigDecimal(String.valueOf(value));
	}

	private static void compare(String label, Object expected, BigDecimal actual, List<String> diffs) {
		String want = (expected == null) ? null : String.valueOf(expected);
		String got = (actual == null) ? null : actual.toPlainString();
		if (!Objects.equals(want, got)) {
			diffs.add(label + " 期望 " + want + " 实际 " + got);
		}
	}

}
