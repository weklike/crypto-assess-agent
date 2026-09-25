package com.cryptoassess.eval.dataset;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.cryptoassess.eval.EvalProperties;
import com.cryptoassess.eval.EvalTool;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.retrieval.Retriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 只在 eval profile 注册标注与校验工具，不进入生产接口。
 */
@Configuration(proxyBeanMethods = false)
@Profile("eval")
class DatasetTools {

	@Bean
	EvalTool annotateTool(@Qualifier("bm25Retriever") Retriever bm25, EvalProperties properties) {
		return new RetrievalAnnotator(bm25, datasetPath(properties),
				new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
	}

	@Bean
	EvalTool validateTool(KbClauseMapper clauseMapper, EvalProperties properties) {
		return new EvalTool() {
			@Override
			public String name() {
				return "validate";
			}

			@Override
			public int run() throws Exception {
				Path path = datasetPath(properties);
				Set<String> known = clauseMapper.findActive()
					.stream()
					.map(KbClause::clauseRef)
					.collect(Collectors.toSet());
				List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
				RetrievalDatasetValidator.Report report = new RetrievalDatasetValidator(known::contains).validate(lines);
				report.issues().forEach(issue -> System.out.println(path + ": " + issue));
				System.out.println(path + ": " + report.queries().size() + " queries, styles " + report.styleCounts()
						+ ", " + report.issues().size() + " issue(s)");
				return report.valid() ? 0 : 1;
			}
		};
	}

	private static Path datasetPath(EvalProperties properties) {
		if (properties.datasetPath() != null && !properties.datasetPath().isBlank()) {
			return Path.of(properties.datasetPath());
		}
		return Path.of(properties.datasetsDir(), "retrieval_queries." + properties.dataset() + ".jsonl");
	}

}
