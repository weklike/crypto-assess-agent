package com.cryptoassess.eval;

/**
 * 一种评测。实现把 report.md、metrics.json、cases.jsonl 写到 context.outputDir()。
 */
public interface EvalSuite {

	String name();

	EvalOutcome run(EvalContext context) throws Exception;

}
