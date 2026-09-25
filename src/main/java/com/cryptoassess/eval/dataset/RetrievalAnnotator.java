package com.cryptoassess.eval.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cryptoassess.eval.EvalFiles;
import com.cryptoassess.eval.EvalTool;
import com.cryptoassess.retrieval.Candidate;
import com.cryptoassess.retrieval.Retriever;
import com.cryptoassess.retrieval.SearchFilter;

/**
 * 命令行标注工具：输入查询 → 显示 BM25 前 20 条 → 选择应命中的条款 → 追加一行到评测集。
 * 只追加，不修改已有行。可以直接输入 clause_ref 标注 BM25 没召回的条款。
 */
public class RetrievalAnnotator implements EvalTool {

	private static final int SHOW = 20;

	private static final Pattern ID = Pattern.compile("^r(\\d+)$");

	private static final Map<String, String> STYLES = Map.of("c", "colloquial", "p", "paraphrase", "k", "keyword");

	private final Retriever bm25;

	private final Path dataset;

	private final BufferedReader in;

	private final PrintStream out;

	public RetrievalAnnotator(Retriever bm25, Path dataset, BufferedReader in, PrintStream out) {
		this.bm25 = bm25;
		this.dataset = dataset;
		this.in = in;
		this.out = out;
	}

	@Override
	public String name() {
		return "annotate";
	}

	@Override
	public int run() throws IOException {
		int nextId = nextId();
		this.out.println("标注文件：" + this.dataset + "（只追加）");
		while (true) {
			String query = prompt("查询（空行退出）> ");
			if (query == null || query.isBlank()) {
				return 0;
			}
			List<Candidate> hits = this.bm25.retrieve(query.strip(), SearchFilter.NONE, SHOW);
			for (int i = 0; i < hits.size(); i++) {
				Candidate hit = hits.get(i);
				this.out.printf("[%d] %s  %s  %s%n", i + 1, hit.clauseRef(), hit.title(), abbreviate(hit.body(), 40));
			}
			List<String> refs = parseSelection(prompt("应命中的序号或 clause_ref，逗号分隔（空行跳过）> "), hits);
			if (refs.isEmpty()) {
				this.out.println("跳过");
				continue;
			}
			String style = null;
			while (style == null) {
				style = STYLES.get(orEmpty(prompt("问法 c=口语 p=改写 k=关键词 > ")).strip());
			}
			String layer = blankToNull(prompt("安全层面（可空）> "));
			String level = blankToNull(prompt("等级 1-4（可空）> "));
			String note = orEmpty(prompt("备注（可空）> ")).strip();

			Map<String, Object> line = new LinkedHashMap<>();
			line.put("id", String.format("r%03d", nextId++));
			line.put("query", query.strip());
			line.put("gold_refs", refs);
			line.put("style", style);
			line.put("layer", layer);
			line.put("level", (level == null) ? null : Integer.valueOf(level));
			line.put("note", note);
			append(EvalFiles.JSON.writeValueAsString(line));
			this.out.println("已追加 " + line.get("id"));
		}
	}

	private List<String> parseSelection(String input, List<Candidate> hits) {
		List<String> refs = new ArrayList<>();
		if (input == null || input.isBlank()) {
			return refs;
		}
		for (String part : input.split("[,，]")) {
			String token = part.strip();
			if (token.isEmpty()) {
				continue;
			}
			String ref = token.matches("\\d+") ? hits.get(Integer.parseInt(token) - 1).clauseRef() : token;
			if (!refs.contains(ref)) {
				refs.add(ref);
			}
		}
		return refs;
	}

	private int nextId() throws IOException {
		if (!Files.exists(this.dataset)) {
			return 1;
		}
		int max = 0;
		for (String line : Files.readAllLines(this.dataset, StandardCharsets.UTF_8)) {
			if (line.isBlank()) {
				continue;
			}
			Matcher m = ID.matcher(EvalFiles.JSON.readTree(line).path("id").asString(""));
			if (m.matches()) {
				max = Math.max(max, Integer.parseInt(m.group(1)));
			}
		}
		return max + 1;
	}

	private void append(String json) throws IOException {
		Files.createDirectories(this.dataset.toAbsolutePath().getParent());
		boolean needsNewline = Files.exists(this.dataset) && Files.size(this.dataset) > 0
				&& !Files.readString(this.dataset, StandardCharsets.UTF_8).endsWith("\n");
		Files.writeString(this.dataset, (needsNewline ? "\n" : "") + json + "\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	private String prompt(String text) throws IOException {
		this.out.print(text);
		this.out.flush();
		return this.in.readLine();
	}

	private static String orEmpty(String value) {
		return (value == null) ? "" : value;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	private static String abbreviate(String text, int max) {
		if (text == null) {
			return "";
		}
		String flat = text.replace('\n', ' ');
		return (flat.length() <= max) ? flat : flat.substring(0, max) + "…";
	}

}
