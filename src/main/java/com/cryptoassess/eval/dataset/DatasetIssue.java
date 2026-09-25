package com.cryptoassess.eval.dataset;

/**
 * @param line 1 起始的行号；0 表示针对整个数据集的问题
 */
public record DatasetIssue(int line, String message) {

	@Override
	public String toString() {
		return (this.line == 0 ? "dataset" : "line " + this.line) + ": " + this.message;
	}

}
