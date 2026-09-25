package com.cryptoassess.eval;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 一次评测运行的上下文。
 *
 * @param datasetPath 为 null 时由 suite 按版本自行定位
 */
public record EvalContext(String suite, String datasetVersion, Path datasetPath, Path outputDir, String gitCommit,
		Instant startedAt) {

}
