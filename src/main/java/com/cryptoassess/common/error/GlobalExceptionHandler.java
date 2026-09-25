package com.cryptoassess.common.error;

import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AppException.class)
	ProblemDetail handleApp(AppException ex) {
		if (ex.type().status().is5xxServerError()) {
			log.warn("Request failed with {}: {}", ex.type().code(), ex.getMessage(), ex.getCause());
		}
		return ProblemDetails.of(ex);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleInvalidBody(MethodArgumentNotValidException ex) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList();
		return ProblemDetails.of(ErrorType.INVALID_ARGUMENT, "请求体校验失败", Map.of("errors", errors));
	}

	@ExceptionHandler({ HandlerMethodValidationException.class, ConstraintViolationException.class })
	ProblemDetail handleInvalidParameter(Exception ex) {
		return ProblemDetails.of(ErrorType.INVALID_ARGUMENT, "请求参数校验失败");
	}

}
