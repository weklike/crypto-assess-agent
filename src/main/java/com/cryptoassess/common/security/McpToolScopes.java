package com.cryptoassess.common.security;

import java.util.Map;

/**
 * 每个 MCP 工具所需的 scope。工具清单写死在代码里，不从配置或数据库加载。
 */
public final class McpToolScopes {

	public static final Map<String, String> REQUIRED = Map.of("search_clauses", "kb:read", "get_clause", "kb:read",
			"check_algorithm", "rules:read", "compute_score", "rules:read");

	private McpToolScopes() {
	}

}
