package com.cryptoassess.common.error;

import java.util.Map;

import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

	private ProblemDetails() {
	}

	public static ProblemDetail of(ErrorType type, String detail) {
		return of(type, detail, Map.of());
	}

	public static ProblemDetail of(ErrorType type, String detail, Map<String, Object> properties) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
		problem.setType(type.typeUri());
		problem.setTitle(type.title());
		properties.forEach(problem::setProperty);
		return problem;
	}

	public static ProblemDetail of(AppException ex) {
		return of(ex.type(), ex.getMessage(), ex.properties());
	}

}
