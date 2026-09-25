package com.cryptoassess.eval;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 运行评测：新建结果目录（已存在即报错）→ 执行 suite → 写 eval_run。
 * 失败的运行同样保留目录（error.txt）并记为 FAILED，不覆盖、不删除。
 */
@Service
public class EvalService {

	private static final Logger log = LoggerFactory.getLogger(EvalService.class);

	private static final DateTimeFormatter DIR_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
		.withZone(ZoneOffset.UTC);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Map<String, EvalSuite> suites;

	private final EvalRunMapper evalRunMapper;

	private final EvalProperties properties;

	private final Clock clock;

	@Autowired
	public EvalService(List<EvalSuite> suites, EvalRunMapper evalRunMapper, EvalProperties properties) {
		this(suites, evalRunMapper, properties, Clock.systemUTC());
	}

	EvalService(List<EvalSuite> suites, EvalRunMapper evalRunMapper, EvalProperties properties, Clock clock) {
		this.suites = suites.stream().collect(Collectors.toMap(EvalSuite::name, Function.identity()));
		this.evalRunMapper = evalRunMapper;
		this.properties = properties;
		this.clock = clock;
	}

	public EvalRunResult run(String suiteName) throws IOException {
		EvalSuite suite = this.suites.get(suiteName);
		if (suite == null) {
			throw new IllegalArgumentException("unknown suite " + suiteName + ", available: " + this.suites.keySet());
		}
		Instant startedAt = this.clock.instant();
		Path outputDir = createOutputDir(suiteName, startedAt);
		Path datasetPath = (this.properties.datasetPath() == null || this.properties.datasetPath().isBlank()) ? null
				: Path.of(this.properties.datasetPath());
		EvalContext context = new EvalContext(suiteName, this.properties.dataset(), datasetPath, outputDir,
				GitInfo.currentCommit(), startedAt);
		try {
			EvalOutcome outcome = suite.run(context);
			record(context, "COMPLETED", outcome.config(), outcome.metrics());
			log.info("Eval {} completed, results in {}", suiteName, outputDir);
			return new EvalRunResult(suiteName, outputDir, true, outcome, null);
		}
		catch (Exception ex) {
			StringWriter trace = new StringWriter();
			ex.printStackTrace(new PrintWriter(trace));
			Files.writeString(outputDir.resolve("error.txt"), trace.toString(), StandardCharsets.UTF_8);
			Map<String, Object> config = new LinkedHashMap<>();
			config.put("gitCommit", context.gitCommit());
			config.put("error", String.valueOf(ex.getMessage()));
			record(context, "FAILED", config, null);
			log.error("Eval {} failed, see {}", suiteName, outputDir.resolve("error.txt"), ex);
			return new EvalRunResult(suiteName, outputDir, false, null, ex.getMessage());
		}
	}

	private Path createOutputDir(String suite, Instant startedAt) throws IOException {
		Path root = Path.of(this.properties.resultsDir());
		Files.createDirectories(root);
		Path dir = root.resolve(DIR_TIME.format(startedAt) + "-" + suite);
		try {
			return Files.createDirectory(dir);
		}
		catch (FileAlreadyExistsException ex) {
			throw new IllegalStateException("result directory already exists, refusing to overwrite: " + dir, ex);
		}
	}

	private void record(EvalContext context, String status, Map<String, Object> config, Map<String, Object> metrics) {
		this.evalRunMapper.insert(context.suite(), context.datasetVersion(), status, JSON.writeValueAsString(config),
				(metrics == null) ? null : JSON.writeValueAsString(metrics), context.outputDir().toString(),
				context.startedAt());
	}

}
