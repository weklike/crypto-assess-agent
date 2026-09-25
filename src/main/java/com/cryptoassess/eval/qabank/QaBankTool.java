package com.cryptoassess.eval.qabank;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.dataset.DatasetIssue;

/**
 * 题库转换与抽样命令行工具，不启动 Spring。输入输出都应在 data/private/ 下（git 忽略）。
 *
 * <pre>
 * java -cp target/classes:$(cat target/classpath.txt) com.cryptoassess.eval.qabank.QaBankTool convert data/private/qa_bank.txt data/private/qa_bank.jsonl
 * java -cp ... com.cryptoassess.eval.qabank.QaBankTool sample data/private/qa_bank.jsonl data/private/qa_bank_sample.v1.jsonl 200 20260924
 * </pre>
 *
 * 输出文件已存在时拒绝覆盖。只打印题号和统计，不打印题目原文。
 */
public final class QaBankTool {

	private QaBankTool() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length >= 3 && "convert".equals(args[0])) {
			System.exit(convert(Path.of(args[1]), Path.of(args[2])));
		}
		if (args.length == 5 && "sample".equals(args[0])) {
			System.exit(sample(Path.of(args[1]), Path.of(args[2]), Integer.parseInt(args[3]), Long.parseLong(args[4])));
		}
		System.err.println("usage: QaBankTool convert <text> <out.jsonl> | sample <bank.jsonl> <out.jsonl> <n> <seed>");
		System.exit(2);
	}

	static int convert(Path input, Path output) throws Exception {
		refuseOverwrite(output);
		QaBankParser.ParseResult result = new QaBankParser()
			.parse(Files.readAllLines(input, StandardCharsets.UTF_8));
		for (DatasetIssue issue : result.issues()) {
			System.out.println(input + ": " + issue);
		}
		write(output, result.items());
		Map<String, Long> byType = countByType(result.items());
		System.out.println("converted " + result.items().size() + " questions " + byType + ", " + result.issues().size()
				+ " issue(s) -> " + output);
		return result.issues().isEmpty() ? 0 : 1;
	}

	static int sample(Path bank, Path output, int size, long seed) throws Exception {
		refuseOverwrite(output);
		List<QaBankItem> items = new ArrayList<>();
		for (String line : Files.readAllLines(bank, StandardCharsets.UTF_8)) {
			if (!line.isBlank()) {
				items.add(EvalFiles.JSON.readValue(line, QaBankItem.class));
			}
		}
		List<QaBankItem> sample = StratifiedSampler.sample(items, size, seed);
		write(output, sample);
		System.out.println("sampled " + sample.size() + " of " + items.size() + " with seed " + seed + " "
				+ countByType(sample) + " -> " + output);
		return 0;
	}

	private static Map<String, Long> countByType(List<QaBankItem> items) {
		return items.stream()
			.collect(java.util.stream.Collectors.groupingBy(QaBankItem::type, java.util.TreeMap::new,
					java.util.stream.Collectors.counting()));
	}

	private static void write(Path output, List<QaBankItem> items) throws Exception {
		StringBuilder out = new StringBuilder();
		for (QaBankItem item : items) {
			out.append(EvalFiles.JSON.writeValueAsString(item)).append('\n');
		}
		if (output.toAbsolutePath().getParent() != null) {
			Files.createDirectories(output.toAbsolutePath().getParent());
		}
		Files.writeString(output, out.toString(), StandardCharsets.UTF_8);
	}

	private static void refuseOverwrite(Path output) {
		if (Files.exists(output)) {
			throw new IllegalStateException(output + " already exists, refusing to overwrite");
		}
	}

}
