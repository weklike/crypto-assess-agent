package com.cryptoassess.common.security;

import java.io.IOException;
import java.util.Map;

import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.common.error.ProblemDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 过滤器层直接写出 ProblemDetail（此时还没进入 Spring MVC 的异常处理）。
 */
class ProblemResponses {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	void unauthorized(HttpServletResponse response, String detail) throws IOException {
		write(response, ErrorType.UNAUTHORIZED, detail, Map.of());
	}

	void forbidden(HttpServletResponse response, String detail, Map<String, Object> properties) throws IOException {
		write(response, ErrorType.FORBIDDEN, detail, properties);
	}

	void write(HttpServletResponse response, ErrorType type, String detail, Map<String, Object> properties)
			throws IOException {
		response.setStatus(type.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		if (type == ErrorType.UNAUTHORIZED) {
			response.setHeader("WWW-Authenticate", "ApiKey header=\"" + ApiKeyAuthenticationFilter.HEADER + "\"");
		}
		response.getWriter().write(JSON.writeValueAsString(ProblemDetails.of(type, detail, properties)));
	}

}
