package com.cryptoassess.common.error;

import java.util.Map;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 转成 ProblemDetail。properties 会作为扩展字段输出。
 */
public class AppException extends RuntimeException {

	private final ErrorType type;

	private final transient Map<String, Object> properties;

	public AppException(ErrorType type, String detail) {
		this(type, detail, Map.of(), null);
	}

	public AppException(ErrorType type, String detail, Throwable cause) {
		this(type, detail, Map.of(), cause);
	}

	public AppException(ErrorType type, String detail, Map<String, Object> properties, Throwable cause) {
		super(detail, cause);
		this.type = type;
		this.properties = Map.copyOf(properties);
	}

	public ErrorType type() {
		return this.type;
	}

	public Map<String, Object> properties() {
		return this.properties;
	}

	public static AppException notFound(String detail) {
		return new AppException(ErrorType.NOT_FOUND, detail);
	}

	public static AppException invalid(String detail) {
		return new AppException(ErrorType.INVALID_ARGUMENT, detail);
	}

	public static AppException unavailable(String dependency, Throwable cause) {
		return new AppException(ErrorType.DEPENDENCY_UNAVAILABLE, dependency + " is unavailable",
				Map.of("dependency", dependency), cause);
	}

}
