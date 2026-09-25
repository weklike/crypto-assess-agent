package com.cryptoassess.assessment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.retrieval.HybridSearchService;
import com.cryptoassess.retrieval.SearchHit;
import com.cryptoassess.retrieval.SearchRequest;
import org.springframework.stereotype.Component;

/**
 * 要求匹配：按安全层面和等级从 kb_clause 取适用的测评指标（每条指标产生一个差距项），
 * 再用检索补充几条相关条款，作为判定时的参考上下文。
 */
@Component
public class RequirementMatcher {

	private final KbClauseMapper clauseMapper;

	private final HybridSearchService searchService;

	private final WorkflowProperties properties;

	public RequirementMatcher(KbClauseMapper clauseMapper, HybridSearchService searchService,
			WorkflowProperties properties) {
		this.clauseMapper = clauseMapper;
		this.searchService = searchService;
		this.properties = properties;
	}

	/**
	 * @param targets 测评指标条款的 clause_ref
	 * @param supplements 检索补充的参考条款 clause_ref（不含指标本身）
	 */
	public record Match(List<String> targets, List<String> supplements) {
	}

	public Match match(int level, AssessObject object) {
		List<String> targets = this.clauseMapper.findRequirements(object.layer(), level)
			.stream()
			.map(KbClause::clauseRef)
			.toList();
		List<String> supplements = new ArrayList<>();
		if (this.properties.supplementK() > 0 && !targets.isEmpty()) {
			Set<String> seen = new LinkedHashSet<>(targets);
			String query = object.name() + " " + (object.description() == null ? "" : object.description()) + " "
					+ JudgmentService.formatMeasures(object.measures());
			List<SearchHit> hits = this.searchService.search(new SearchRequest(truncate(query, 500),
					this.properties.supplementMode(), this.properties.supplementK() + targets.size(), object.layer(),
					level)).hits();
			for (SearchHit hit : hits) {
				if (supplements.size() >= this.properties.supplementK()) {
					break;
				}
				if (seen.add(hit.clauseRef())) {
					supplements.add(hit.clauseRef());
				}
			}
		}
		return new Match(targets, supplements);
	}

	private static String truncate(String text, int max) {
		return (text.length() <= max) ? text : text.substring(0, max);
	}

}
