package com.cryptoassess.rules;

/**
 * 规则表 YAML 不合法。启动时抛出，应用直接启动失败，错误信息指明位置和原因。
 */
public class RuleTableException extends RuntimeException {

	public RuleTableException(String message) {
		super(message);
	}

	public RuleTableException(String message, Throwable cause) {
		super(message, cause);
	}

}
