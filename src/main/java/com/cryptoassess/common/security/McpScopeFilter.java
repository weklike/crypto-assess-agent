package com.cryptoassess.common.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import com.cryptoassess.common.audit.AuditService;
import com.cryptoassess.common.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 的所有工具都走同一个 /mcp 端点，按 URL 无法区分权限：这里读出 JSON-RPC 请求体，
 * 对 tools/call 按工具名检查 scope，不足时返回 403 并写审计。请求体限制 256 KB。
 */
class McpScopeFilter extends OncePerRequestFilter {

	private static final int MAX_BODY = 256 * 1024;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ProblemResponses problems;

	private final AuditService auditService;

	McpScopeFilter(ProblemResponses problems, AuditService auditService) {
		this.problems = problems;
		this.auditService = auditService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			chain.doFilter(request, response);
			return;
		}
		byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
		if (body.length > MAX_BODY) {
			this.problems.write(response, ErrorType.INVALID_ARGUMENT, "request body exceeds " + MAX_BODY + " bytes",
					Map.of());
			return;
		}
		String tool = toolName(body);
		if (tool != null) {
			String required = McpToolScopes.REQUIRED.get(tool);
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			boolean allowed = required != null && auth != null && auth.getAuthorities()
				.stream()
				.anyMatch(a -> a.getAuthority().equals("SCOPE_" + required));
			if (!allowed) {
				String actor = (auth == null) ? "anonymous" : auth.getName();
				this.auditService.record(actor, "mcp", tool, null, AuditService.sha256(body), AuditService.DENIED);
				this.problems.forbidden(response,
						(required == null) ? "unknown tool " + tool : "tool " + tool + " requires scope " + required,
						(required == null) ? Map.of() : Map.of("requiredScope", required));
				return;
			}
		}
		chain.doFilter(new CachedBodyRequest(request, body), response);
	}

	/** 只关心 tools/call；其他 JSON-RPC 方法（initialize、tools/list 等）返回 null。 */
	private static String toolName(byte[] body) {
		try {
			JsonNode node = JSON.readTree(body);
			if (node != null && "tools/call".equals(node.path("method").asString(""))) {
				return node.path("params").path("name").asString("");
			}
		}
		catch (JacksonException ex) {
			// 交给 MCP 传输层返回 JSON-RPC 解析错误
		}
		return null;
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream in = new ByteArrayInputStream(this.body);
			return new ServletInputStream() {
				@Override
				public int read() {
					return in.read();
				}

				@Override
				public int read(byte[] b, int off, int len) {
					return in.read(b, off, len);
				}

				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public java.io.BufferedReader getReader() {
			return new java.io.BufferedReader(
					new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
		}

		@Override
		public int getContentLength() {
			return this.body.length;
		}

		@Override
		public long getContentLengthLong() {
			return this.body.length;
		}

	}

}
