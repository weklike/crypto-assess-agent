package com.cryptoassess.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * 评测输出文件的读写：metrics.json、cases.jsonl、report.md。
 */
public final class EvalFiles {

	public static final JsonMapper JSON = JsonMapper.builder().build();

	private EvalFiles() {
	}

	public static void writeMetrics(Path dir, Map<String, Object> config, Map<String, Object> metrics)
			throws IOException {
		Files.writeString(dir.resolve("metrics.json"),
				JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("config", config, "metrics", metrics)),
				StandardCharsets.UTF_8);
	}

	public static void writeCases(Path dir, List<? extends Map<String, ?>> cases) throws IOException {
		StringBuilder out = new StringBuilder();
		for (Map<String, ?> line : cases) {
			out.append(JSON.writeValueAsString(line)).append('\n');
		}
		Files.writeString(dir.resolve("cases.jsonl"), out.toString(), StandardCharsets.UTF_8);
	}

	public static void writeReport(Path dir, String markdown) throws IOException {
		Files.writeString(dir.resolve("report.md"), markdown, StandardCharsets.UTF_8);
	}

	public static String sha256(String text) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
