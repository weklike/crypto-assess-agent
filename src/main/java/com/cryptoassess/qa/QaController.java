package com.cryptoassess.qa;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class QaController {

	private static final Logger log = LoggerFactory.getLogger(QaController.class);

	private final QaService qaService;

	private final QaProperties properties;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public QaController(QaService qaService, QaProperties properties) {
		this.qaService = qaService;
		this.properties = properties;
	}

	/**
	 * 检索和拒答判断同步完成（失败直接返回 ProblemDetail），之后在虚拟线程里流式输出。
	 */
	@PostMapping(path = "/api/qa", produces = { MediaType.TEXT_EVENT_STREAM_VALUE,
			MediaType.APPLICATION_PROBLEM_JSON_VALUE })
	public SseEmitter ask(@Valid @RequestBody QaRequest request) {
		PreparedQuestion prepared = this.qaService.prepare(request);
		SseEmitter emitter = new SseEmitter(this.properties.timeout().plusSeconds(30).toMillis());
		this.executor.execute(() -> {
			try {
				this.qaService.answer(prepared,
						(event, data) -> emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON)));
				emitter.complete();
			}
			catch (IOException ex) {
				log.info("QA stream closed by client");
				emitter.completeWithError(ex);
			}
			catch (RuntimeException ex) {
				log.error("QA stream failed", ex);
				emitter.completeWithError(ex);
			}
		});
		return emitter;
	}

	@PreDestroy
	void shutdown() {
		this.executor.shutdownNow();
	}

}
