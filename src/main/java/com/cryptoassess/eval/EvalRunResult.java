package com.cryptoassess.eval;

import java.nio.file.Path;

public record EvalRunResult(String suite, Path outputDir, boolean completed, EvalOutcome outcome, String error) {

	public int exitCode() {
		return this.completed ? 0 : 1;
	}

}
