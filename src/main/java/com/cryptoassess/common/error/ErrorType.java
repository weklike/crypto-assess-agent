package com.cryptoassess.common.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

/**
 * 全部业务错误类型。ProblemDetail 的 type 为 urn:crypto-assess:error:&lt;code&gt;。
 */
public enum ErrorType {

	INVALID_ARGUMENT("invalid-argument", HttpStatus.BAD_REQUEST, "请求参数不合法"),
	NORMALIZED_FORMAT("normalized-format", HttpStatus.BAD_REQUEST, "规范化标准文件格式错误"),
	UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "未认证"),
	FORBIDDEN("forbidden", HttpStatus.FORBIDDEN, "权限不足"),
	NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "资源不存在"),
	STATE_CONFLICT("state-conflict", HttpStatus.CONFLICT, "当前状态不允许该操作"),
	RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
	NOT_IMPLEMENTED("not-implemented", HttpStatus.NOT_IMPLEMENTED, "功能尚未实现"),
	LLM_INVALID_OUTPUT("llm-invalid-output", HttpStatus.BAD_GATEWAY, "模型输出不符合约定格式"),
	DEPENDENCY_UNAVAILABLE("dependency-unavailable", HttpStatus.SERVICE_UNAVAILABLE, "外部依赖不可用"),
	LLM_TIMEOUT("llm-timeout", HttpStatus.GATEWAY_TIMEOUT, "模型调用超时"),
	INTERNAL("internal", HttpStatus.INTERNAL_SERVER_ERROR, "内部错误");

	private final String code;

	private final HttpStatus status;

	private final String title;

	ErrorType(String code, HttpStatus status, String title) {
		this.code = code;
		this.status = status;
		this.title = title;
	}

	public String code() {
		return this.code;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String title() {
		return this.title;
	}

	public URI typeUri() {
		return URI.create("urn:crypto-assess:error:" + this.code);
	}

}
