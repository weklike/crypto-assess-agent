package com.cryptoassess.assessment;

import java.util.List;

/**
 * 一次结构化判定的输入：一个测评对象 × 一组测评指标条款。
 */
public record JudgeInput(int level, String objectName, String layer, String description, CryptoMeasures measures,
		List<Clause> clauses) {

	public record Clause(String clauseRef, String title, String body) {
	}

}
