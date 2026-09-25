package com.cryptoassess.eval.dataset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.cryptoassess.knowledge.format.NormalizedFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 检索评测集校验：JSON 格式、字段取值、id 唯一、gold_refs 必须存在于 kb_clause、三种问法都要有。
 */
public class RetrievalDatasetValidator {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Predicate<String> clauseExists;

	/**
	 * @param clauseExists 判断条款引用是否存在于 kb_clause
	 */
	public RetrievalDatasetValidator(Predicate<String> clauseExists) {
		this.clauseExists = clauseExists;
	}

	public record Report(List<RetrievalQuery> queries, List<DatasetIssue> issues, Map<String, Long> styleCounts) {

		public boolean valid() {
			return this.issues.isEmpty();
		}

	}

	public Report validate(List<String> lines) {
		List<RetrievalQuery> queries = new ArrayList<>();
		List<DatasetIssue> issues = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			int lineNo = i + 1;
			String line = lines.get(i).strip();
			if (line.isEmpty()) {
				continue;
			}
			JsonNode node;
			try {
				node = JSON.readTree(line);
			}
			catch (JacksonException ex) {
				issues.add(new DatasetIssue(lineNo, "invalid JSON"));
				continue;
			}
			RetrievalQuery query = toQuery(node);
			checkFields(query, lineNo, issues);
			if (query.id() != null && !ids.add(query.id())) {
				issues.add(new DatasetIssue(lineNo, "duplicate id " + query.id()));
			}
			queries.add(query);
		}
		Map<String, Long> styleCounts = new LinkedHashMap<>();
		for (String style : RetrievalQuery.STYLES) {
			long count = queries.stream().filter(q -> style.equals(q.style())).count();
			styleCounts.put(style, count);
			if (count == 0) {
				issues.add(new DatasetIssue(0, "no query with style " + style));
			}
		}
		return new Report(List.copyOf(queries), List.copyOf(issues), styleCounts);
	}

	private void checkFields(RetrievalQuery query, int lineNo, List<DatasetIssue> issues) {
		if (query.id() == null || query.id().isBlank()) {
			issues.add(new DatasetIssue(lineNo, "id is required"));
		}
		if (query.query() == null || query.query().isBlank()) {
			issues.add(new DatasetIssue(lineNo, "query is required"));
		}
		if (query.goldRefs() == null || query.goldRefs().isEmpty()) {
			issues.add(new DatasetIssue(lineNo, "gold_refs must not be empty"));
		}
		else {
			query.goldRefs()
				.stream()
				.filter(ref -> !this.clauseExists.test(ref))
				.forEach(ref -> issues.add(new DatasetIssue(lineNo, "gold_ref not in kb_clause: " + ref)));
		}
		if (!RetrievalQuery.STYLES.contains(query.style())) {
			issues.add(new DatasetIssue(lineNo, "style must be one of " + RetrievalQuery.STYLES));
		}
		if (query.layer() != null && !NormalizedFormat.LAYERS.contains(query.layer())) {
			issues.add(new DatasetIssue(lineNo, "layer is not a valid 安全层面: " + query.layer()));
		}
		if (query.level() != null && (query.level() < 1 || query.level() > 4)) {
			issues.add(new DatasetIssue(lineNo, "level must be 1-4"));
		}
	}

	private static RetrievalQuery toQuery(JsonNode node) {
		List<String> goldRefs = null;
		JsonNode refs = node.get("gold_refs");
		if (refs != null && refs.isArray()) {
			goldRefs = new ArrayList<>();
			for (JsonNode ref : refs) {
				goldRefs.add(ref.asString());
			}
		}
		JsonNode level = node.get("level");
		return new RetrievalQuery(text(node, "id"), text(node, "query"), goldRefs, text(node, "style"),
				text(node, "layer"), (level == null || level.isNull()) ? null : level.asInt(), text(node, "note"));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || value.isNull()) ? null : value.asString();
	}

}
