package com.cryptoassess.knowledge.format;

/**
 * @param line 1 起始的行号
 */
public record LintIssue(int line, String message) {

	@Override
	public String toString() {
		return "line " + this.line + ": " + this.message;
	}

}
