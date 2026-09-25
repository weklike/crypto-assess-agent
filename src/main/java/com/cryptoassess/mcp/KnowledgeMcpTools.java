package com.cryptoassess.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.format.NormalizedFormat;
import com.cryptoassess.retrieval.HybridSearchService;
import com.cryptoassess.retrieval.SearchHit;
import com.cryptoassess.retrieval.SearchMode;
import com.cryptoassess.retrieval.SearchRequest;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 知识库只读工具（scope kb:read）。工具名和描述写死在注解里，运行时不修改。
 */
@Component
public class KnowledgeMcpTools {

	private static final Pattern CLAUSE_REF = Pattern
		.compile("^[A-Z]{1,4}(/[A-Z]{1,2})? \\d+(\\.\\d+)?-\\d{4}#([0-9]+(\\.[0-9]+)*|[A-Z](\\.[0-9]+)*)$");

	private final HybridSearchService searchService;

	private final KnowledgeIngestService knowledgeService;

	private final McpToolSupport support;

	public KnowledgeMcpTools(HybridSearchService searchService, KnowledgeIngestService knowledgeService,
			McpToolSupport support) {
		this.searchService = searchService;
		this.knowledgeService = knowledgeService;
		this.support = support;
	}

	@McpTool(name = "search_clauses",
			description = "在商用密码应用安全性评估相关标准中检索条款（BM25 + 向量混合检索）。返回条款引用、标题、摘要和分数，最多 10 条。只读。",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public String searchClauses(@McpToolParam(description = "检索问题或关键词，最多 200 字") String query,
			@McpToolParam(description = "安全层面过滤，如 网络和通信；可省略", required = false) String layer,
			@McpToolParam(description = "等级过滤 1-4；可省略", required = false) Integer level,
			@McpToolParam(description = "返回条数 1-10，默认 5", required = false) Integer k) {
		Map<String, Object> args = new LinkedHashMap<>();
		args.put("query", query);
		args.put("layer", layer);
		args.put("level", level);
		args.put("k", k);
		return this.support.audited("search_clauses", args, () -> {
			String q = McpToolSupport.require(query, "query", 200);
			if (layer != null && !layer.isBlank() && !NormalizedFormat.LAYERS.contains(layer)) {
				throw new IllegalArgumentException("layer must be one of " + NormalizedFormat.LAYERS);
			}
			if (level != null && (level < 1 || level > 4)) {
				throw new IllegalArgumentException("level must be 1-4");
			}
			int size = (k == null) ? 5 : k;
			if (size < 1 || size > 10) {
				throw new IllegalArgumentException("k must be 1-10");
			}
			List<SearchHit> hits = this.searchService
				.search(new SearchRequest(q, SearchMode.HYBRID, size, layer, level))
				.hits();
			return hits.stream().map(h -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("clauseRef", h.clauseRef());
				m.put("title", h.title());
				m.put("snippet", McpToolSupport.truncate(h.snippet(), 200));
				m.put("score", h.rrfScore());
				return m;
			}).toList();
		});
	}

	@McpTool(name = "get_clause", description = "按条款引用（格式 标准号#条款号，如 GB/T 39786-2021#6.2.1）获取条款全文与章节路径。只读。",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = false))
	public String getClause(@McpToolParam(description = "条款引用，格式 标准号#条款号") String clause_ref) {
		return this.support.audited("get_clause", Map.of("clause_ref", String.valueOf(clause_ref)), () -> {
			String ref = McpToolSupport.require(clause_ref, "clause_ref", 128);
			if (!CLAUSE_REF.matcher(ref).matches()) {
				throw new IllegalArgumentException("clause_ref must look like 标准号#条款号");
			}
			KbClause clause = this.knowledgeService.findClause(ref)
				.orElseThrow(() -> new IllegalArgumentException("clause not found: " + ref));
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("clauseRef", clause.clauseRef());
			m.put("path", clause.path());
			m.put("title", clause.clauseNo() + " " + clause.title());
			m.put("layer", clause.layer());
			m.put("levels", clause.levels());
			m.put("body", McpToolSupport.truncate(clause.body(), 4000));
			m.put("truncated", clause.body() != null && clause.body().length() > 4000);
			return m;
		});
	}

}
