package com.cryptoassess.eval;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 读取当前 git commit，工作区有未提交改动时加 -dirty 后缀；拿不到时返回 unknown。
 */
public final class GitInfo {

	private GitInfo() {
	}

	public static String currentCommit() {
		String head = run("git", "rev-parse", "--short=12", "HEAD");
		if (head == null || head.isBlank()) {
			return "unknown";
		}
		String status = run("git", "status", "--porcelain");
		return (status != null && !status.isBlank()) ? head + "-dirty" : head;
	}

	private static String run(String... command) {
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
			String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
			if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
				return null;
			}
			return out;
		}
		catch (java.io.IOException ex) {
			return null;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

}
