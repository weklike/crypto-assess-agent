package com.cryptoassess.eval;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * eval profile 的入口：执行 --eval.suite 或 --eval.tool 后退出。退出码 0 表示完成，1 表示失败。
 */
@Component
@Profile("eval")
class EvalRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

	private final EvalService evalService;

	private final Map<String, EvalTool> tools;

	private final EvalProperties properties;

	private final ConfigurableApplicationContext context;

	EvalRunner(EvalService evalService, Map<String, EvalTool> tools, EvalProperties properties,
			ConfigurableApplicationContext context) {
		this.evalService = evalService;
		this.tools = tools;
		this.properties = properties;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		int code;
		try {
			code = execute();
		}
		catch (Exception ex) {
			log.error("Eval run failed before producing results", ex);
			code = 1;
		}
		int exitCode = code;
		System.exit(SpringApplication.exit(this.context, () -> exitCode));
	}

	private int execute() throws Exception {
		if (this.properties.tool() != null && !this.properties.tool().isBlank()) {
			EvalTool tool = this.tools.values()
				.stream()
				.filter(t -> t.name().equals(this.properties.tool()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown tool " + this.properties.tool()));
			return tool.run();
		}
		if (this.properties.suite() == null || this.properties.suite().isBlank()) {
			log.error("usage: --eval.suite=retrieval|qa|gap|scoring|summary or --eval.tool=annotate|validate");
			return 1;
		}
		EvalRunResult result = this.evalService.run(this.properties.suite());
		log.info("Results: {}", result.outputDir());
		return result.exitCode();
	}

}
