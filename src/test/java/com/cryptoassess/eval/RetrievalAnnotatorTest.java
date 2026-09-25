package com.cryptoassess.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.cryptoassess.eval.dataset.RetrievalAnnotator;
import com.cryptoassess.retrieval.Candidate;
import com.cryptoassess.retrieval.Retriever;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalAnnotatorTest {

	@TempDir
	Path dir;

	private final Retriever bm25 = (query, filter, size) -> List.of(
			new Candidate("FIX/T 0001-2026#5.4.3", "5.4.3 重要数据存储机密性", "5 技术要求 > 5.4 应用和数据", "数据库中…", "应用和数据", 9, 1),
			new Candidate("FIX/T 0001-2026#5.2.3", "5.2.3 通信过程中重要数据的机密性", "路径", "重要数据…", "网络和通信", 7, 2));

	@Test
	void appendsSelectedRefsAndKeepsExistingLines() throws Exception {
		Path dataset = dir.resolve("retrieval_queries.v1.jsonl");
		String existing = "{\"id\":\"r007\",\"query\":\"旧\",\"gold_refs\":[\"X#1\"],\"style\":\"keyword\"}";
		Files.writeString(dataset, existing + "\n");
		String input = String.join("\n", "身份证号存库要加密吗", "1, 2", "c", "应用和数据", "3", "备注", "", "");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		int code = new RetrievalAnnotator(bm25, dataset, new BufferedReader(new StringReader(input)),
				new PrintStream(out, true, StandardCharsets.UTF_8)).run();

		List<String> lines = Files.readAllLines(dataset);
		assertThat(code).isZero();
		assertThat(lines).hasSize(2);
		assertThat(lines.get(0)).isEqualTo(existing);
		assertThat(lines.get(1)).contains("\"id\":\"r008\"")
			.contains("\"query\":\"身份证号存库要加密吗\"")
			.contains("\"gold_refs\":[\"FIX/T 0001-2026#5.4.3\",\"FIX/T 0001-2026#5.2.3\"]")
			.contains("\"style\":\"colloquial\"")
			.contains("\"layer\":\"应用和数据\"")
			.contains("\"level\":3");
		assertThat(out.toString(StandardCharsets.UTF_8)).contains("[1] FIX/T 0001-2026#5.4.3");
	}

	@Test
	void skippingSelectionWritesNothingAndMissingFileIsCreated() throws Exception {
		Path dataset = dir.resolve("new.jsonl");
		String input = String.join("\n", "某查询", "", "另一个查询", "FIX/T 0001-2026#9.9", "k", "", "", "", "");

		new RetrievalAnnotator(bm25, dataset, new BufferedReader(new StringReader(input)),
				new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)).run();

		List<String> lines = Files.readAllLines(dataset);
		assertThat(lines).hasSize(1);
		assertThat(lines.get(0)).contains("\"id\":\"r001\"")
			.contains("另一个查询")
			.contains("FIX/T 0001-2026#9.9")
			.contains("\"layer\":null");
	}

}
