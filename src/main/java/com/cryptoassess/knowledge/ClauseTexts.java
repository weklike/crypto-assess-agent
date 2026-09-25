package com.cryptoassess.knowledge;

/**
 * 条款文本拼接规则，索引和评测共用。
 */
public final class ClauseTexts {

	private ClauseTexts() {
	}

	/** 向量化文本 = 章节路径 + 编号标题 + 正文，让短条款也带上所在章节的语境。 */
	public static String embeddingText(KbClause clause) {
		StringBuilder text = new StringBuilder();
		if (clause.path() != null && !clause.path().isEmpty()) {
			text.append(clause.path()).append('\n');
		}
		text.append(clause.clauseNo()).append(' ').append(clause.title());
		if (clause.body() != null && !clause.body().isEmpty()) {
			text.append('\n').append(clause.body());
		}
		return text.toString();
	}

}
