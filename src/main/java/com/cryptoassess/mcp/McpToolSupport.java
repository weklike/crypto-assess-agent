package com.cryptoassess.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.cryptoassess.common.audit.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 工具的公共部分：执行并写审计（参数只存规范化 JSON 的哈希）、限制返回大小。
 * 工具在请求线程上执行（Spring AI 对 Servlet 同步服务开启了 immediateExecution），因此能拿到认证信息。
 */
@Component
class McpToolSupport {

	static final int MAX_RESULT_CHARS = 16_000;

	static final JsonMapper JSON = JsonMapper.builder().build();

	private final AuditService auditService;

	McpToolSupport(AuditService auditService) {
		this.auditService = auditService;
	}

	String audited(String tool, Map<String, Object> args, Supplier<Object> action) {
		String actor = actor();
		String argsHash = AuditService.sha256(JSON.writeValueAsString(canonical(args)));
		try {
			String json = JSON.writeValueAsString(action.get());
			if (json.length() > MAX_RESULT_CHARS) {
				throw new IllegalStateException("result exceeds " + MAX_RESULT_CHARS + " characters");
			}
			this.auditService.record(actor, "mcp", tool, null, argsHash, AuditService.OK);
			return json;
		}
		catch (RuntimeException ex) {
			this.auditService.record(actor, "mcp", tool, null, argsHash, AuditService.ERROR);
			throw ex;
		}
	}

	private static String actor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return (auth == null) ? "anonymous" : auth.getName();
	}

	/** 键排序后的 JSON，同样的参数总是得到同样的哈希。 */
	@SuppressWarnings("unchecked")
	static Object canonical(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> sorted = new TreeMap<>();
			map.forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
			return sorted;
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>();
			list.forEach(v -> out.add(canonical(v)));
			return out;
		}
		return value;
	}

	static String require(String value, String name, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		if (value.length() > maxLength) {
			throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
		}
		return value.strip();
	}

	static String truncate(String text, int max) {
		if (text == null) {
			return null;
		}
		return (text.length() <= max) ? text : text.substring(0, max) + "…";
	}

}
