package com.cryptoassess.knowledge.format;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;

/**
 * 规范化文件未通过格式检查。整份文件不入库，issues 带行号返回给调用方。
 */
public class NormalizedFormatException extends AppException {

	private static final int MAX_ISSUES_IN_MESSAGE = 5;

	private final transient List<LintIssue> issues;

	public NormalizedFormatException(List<LintIssue> issues) {
		super(ErrorType.NORMALIZED_FORMAT, summarize(issues), Map.of("issues", List.copyOf(issues)), null);
		this.issues = List.copyOf(issues);
	}

	public List<LintIssue> issues() {
		return this.issues;
	}

	private static String summarize(List<LintIssue> issues) {
		String head = issues.stream()
			.limit(MAX_ISSUES_IN_MESSAGE)
			.map(LintIssue::toString)
			.collect(Collectors.joining("; "));
		return issues.size() + " issue(s): " + head + ((issues.size() > MAX_ISSUES_IN_MESSAGE) ? "; …" : "");
	}

}
