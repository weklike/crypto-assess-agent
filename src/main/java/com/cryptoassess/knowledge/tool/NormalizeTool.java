package com.cryptoassess.knowledge.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.cryptoassess.knowledge.format.LintIssue;
import com.cryptoassess.knowledge.format.NormalizedFormatLinter;
import com.cryptoassess.knowledge.format.PdfDraftConverter;

/**
 * 语料规范化命令行工具，不启动 Spring。用法（先 ./mvnw -q compile）：
 *
 * <pre>
 * java -cp target/classes com.cryptoassess.knowledge.tool.NormalizeTool sha256 data/raw/x.pdf
 * java -cp target/classes com.cryptoassess.knowledge.tool.NormalizeTool draft data/raw/x.pdf "GB/T 39786-2021" "标题"
 * java -cp target/classes com.cryptoassess.knowledge.tool.NormalizeTool lint data/normalized/x.md ...
 * </pre>
 *
 * 文本提取调用系统的 pdftotext（poppler-utils）。初稿写到 data/normalized/&lt;文件名&gt;.draft.md，已存在时不覆盖。
 */
public final class NormalizeTool {

	private NormalizeTool() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			usage();
			System.exit(2);
		}
		int exit = switch (args[0]) {
			case "sha256" -> {
				System.out.println(sha256(Path.of(args[1])));
				yield 0;
			}
			case "draft" -> draft(args);
			case "lint" -> lint(List.of(args).subList(1, args.length));
			default -> {
				usage();
				yield 2;
			}
		};
		System.exit(exit);
	}

	private static int draft(String[] args) throws IOException, InterruptedException {
		if (args.length != 4) {
			usage();
			return 2;
		}
		Path pdf = Path.of(args[1]);
		String name = pdf.getFileName().toString().replaceFirst("\\.pdf$", "");
		Path out = Path.of("data/normalized", name + ".draft.md");
		if (Files.exists(out)) {
			System.err.println(out + " already exists, refusing to overwrite");
			return 1;
		}
		Process process = new ProcessBuilder("pdftotext", "-enc", "UTF-8", pdf.toString(), "-")
			.redirectError(ProcessBuilder.Redirect.INHERIT)
			.start();
		String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.waitFor() != 0) {
			System.err.println("pdftotext failed with exit code " + process.exitValue());
			return 1;
		}
		String draft = new PdfDraftConverter().convert(text.lines().toList(), args[2], args[3], sha256(pdf));
		Files.createDirectories(out.getParent());
		Files.writeString(out, draft, StandardCharsets.UTF_8);
		System.out.println("draft written to " + out + "; review it, then rename to " + name + ".md and run lint");
		return 0;
	}

	private static int lint(List<String> files) throws IOException {
		NormalizedFormatLinter linter = new NormalizedFormatLinter();
		int total = 0;
		for (String file : files) {
			List<LintIssue> issues = linter.lint(Files.readAllLines(Path.of(file), StandardCharsets.UTF_8));
			issues.forEach(issue -> System.out.println(file + ":" + issue.line() + ": " + issue.message()));
			System.out.println(file + ": " + issues.size() + " issue(s)");
			total += issues.size();
		}
		return (total == 0) ? 0 : 1;
	}

	static String sha256(Path file) throws IOException {
		try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
			in.transferTo(java.io.OutputStream.nullOutputStream());
			return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void usage() {
		System.err.println("usage: NormalizeTool sha256 <file> | draft <pdf> <doc_code> <title> | lint <md>...");
	}

}
