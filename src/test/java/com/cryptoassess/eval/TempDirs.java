package com.cryptoassess.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spring 上下文在 JUnit 注入静态 @TempDir 之前加载，需要在静态初始化时自己建临时目录。
 */
final class TempDirs {

	private TempDirs() {
	}

	static Path create(String prefix) {
		try {
			return Files.createTempDirectory(prefix);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
