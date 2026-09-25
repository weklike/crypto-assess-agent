package com.cryptoassess.qa;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.common.error.ProblemDetails;
import com.cryptoassess.common.llm.LlmCall;
import com.cryptoassess.common.llm.LlmCallRecorder;
import com.cryptoassess.common.llm.LlmCallStatus;
import com.cryptoassess.common.llm.LlmErrors;
import com.cryptoassess.common.llm.LlmRequestOptions;
import com.cryptoassess.common.llm.PromptTemplates;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.qa.CitationValidator.CitationCheck;
import com.cryptoassess.retrieval.HybridSearchService;
import com.cryptoassess.retrieval.RetrievalProperties;
import com.cryptoassess.retrieval.SearchHit;
import com.cryptoassess.retrieval.SearchMode;
import com.cryptoassess.retrieval.SearchRequest;
import com.cryptoassess.retrieval.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import tools.jackson.databind.json.JsonMapper;

/**
 * 检索增强问答：检索 → 拒答判断 → 流式调用模型 → 汇总全文后解析并校验引用 → 落库。
 * 引用在全文结束后统一校验：流式片段里的引用可能被切断，逐片校验不可靠。
 */
@Service
public class QaService {

	private static final Logger log = LoggerFactory.getLogger(QaService.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	static final String REFUSAL = "检索到的条款与问题的相关度不足，依据现有条款无法可靠回答。可以参考上面列出的最接近的条款，或换一种问法。";

	private final HybridSearchService searchService;

	private final ChatModel chatModel;

	private final LlmCallRecorder llmCallRecorder;

	private final QaRecordMapper qaRecordMapper;

	private final KbClauseMapper clauseMapper;

	private final QaProperties properties;

	private final RetrievalProperties retrievalProperties;

	private final String configuredModel;

	private final LlmRequestOptions requestOptions;

	public QaService(HybridSearchService searchService, ChatModel chatModel, LlmCallRecorder llmCallRecorder,
			QaRecordMapper qaRecordMapper, KbClauseMapper clauseMapper, QaProperties properties,
			RetrievalProperties retrievalProperties, @Value("${spring.ai.openai.chat.model:unknown}") String model,
			LlmRequestOptions requestOptions) {
		this.requestOptions = requestOptions;
		this.searchService = searchService;
		this.chatModel = chatModel;
		this.llmCallRecorder = llmCallRecorder;
		this.qaRecordMapper = qaRecordMapper;
		this.clauseMapper = clauseMapper;
		this.properties = properties;
		this.retrievalProperties = retrievalProperties;
		this.configuredModel = model;
	}

	/**
	 * 同步完成检索和拒答判断。依赖不可用时在这里抛出，接口直接返回 503，而不是先开流再报错。
	 */
	public PreparedQuestion prepare(QaRequest request) {
		SearchMode mode = (request.mode() != null) ? request.mode() : this.properties.mode();
		SearchResponse search = this.searchService.search(
				new SearchRequest(request.question(), mode, this.properties.topK(), request.layer(), request.level()));
		Double topScore = search.hits().isEmpty() ? null : topScore(search.hits().get(0), mode);
		boolean refused = search.hits().isEmpty() || (mode == SearchMode.HYBRID_RERANK && topScore != null
				&& topScore < this.retrievalProperties.refuseThreshold());
		return new PreparedQuestion(request, search, topScore, refused);
	}

	public void answer(PreparedQuestion prepared, QaEventSink sink) throws IOException {
		long start = System.nanoTime();
		SearchResponse search = prepared.search();
		sink.send("meta", meta(prepared));
		if (prepared.refused()) {
			sink.send("token", Map.of("text", REFUSAL));
			sink.send("citations", citationsEvent(new CitationCheck(List.of(), List.of()), search));
			long id = saveRecord(prepared, REFUSAL, new CitationCheck(List.of(), List.of()), null, null, start);
			sink.send("done", done(id, true, start, null, null, null, List.of()));
			return;
		}

		String promptText = PromptTemplates.render(this.properties.promptVersion(),
				Map.of("clauses", formatClauses(search.hits()), "question", prepared.request().question()));
		ChatOptions options = this.requestOptions.builder(this.chatModel, this.properties.temperature()).build();
		StringBuilder answer = new StringBuilder();
		Usage[] usage = new Usage[1];
		String[] model = { this.configuredModel };
		Long[] firstTokenMs = new Long[1];
		Duration timeout = this.properties.timeout();
		try {
			this.chatModel.stream(new Prompt(promptText, options)).timeout(timeout).doOnNext(chunk -> {
				String text = text(chunk);
				if (text != null && !text.isEmpty()) {
					if (firstTokenMs[0] == null) {
						firstTokenMs[0] = millisSince(start);
					}
					answer.append(text);
					sendUnchecked(sink, "token", Map.of("text", text));
				}
				if (chunk.getMetadata() != null) {
					Usage u = chunk.getMetadata().getUsage();
					if (u != null && u.getPromptTokens() != null && u.getPromptTokens() > 0) {
						usage[0] = u;
					}
					String m = chunk.getMetadata().getModel();
					if (m != null && !m.isBlank()) {
						model[0] = m;
					}
				}
			}).blockLast(timeout.plusSeconds(1));
		}
		catch (RuntimeException ex) {
			Throwable cause = Exceptions.unwrap(ex);
			boolean timedOut = LlmErrors.isTimeout(cause)
					|| (cause instanceof IllegalStateException && String.valueOf(cause.getMessage()).contains("Timeout"));
			if (cause instanceof SinkFailure failure) {
				throw failure.getCause();
			}
			LlmCallStatus status = timedOut ? LlmCallStatus.TIMEOUT : LlmCallStatus.ERROR;
			recordCall(model[0], status, timedOut ? "timeout" : cause.getClass().getSimpleName(), start, usage[0]);
			log.warn("QA model call failed with {}", status);
			ErrorType type = timedOut ? ErrorType.LLM_TIMEOUT : ErrorType.DEPENDENCY_UNAVAILABLE;
			sink.send("error", ProblemDetails.of(type,
					timedOut ? "模型调用超过 " + timeout.toSeconds() + " 秒未完成" : "模型服务调用失败"));
			return;
		}
		if (answer.toString().isBlank()) {
			recordCall(model[0], LlmCallStatus.EMPTY, "empty", start, usage[0]);
			sink.send("error", ProblemDetails.of(ErrorType.LLM_INVALID_OUTPUT, "模型返回了空回复"));
			return;
		}
		long llmCallId = recordCall(model[0], LlmCallStatus.OK, null, start, usage[0]);
		CitationCheck check = validateCitations(answer.toString(), search);
		sink.send("citations", citationsEvent(check, search));
		long id = saveRecord(prepared, answer.toString(), check, llmCallId, firstTokenMs[0], start);
		List<String> formatIssues = AnswerFormat.markdownIssues(answer.toString());
		if (!formatIssues.isEmpty()) {
			log.info("QA answer contains markdown markers {}", formatIssues);
		}
		sink.send("done", done(id, false, start, firstTokenMs[0], usage[0], model[0], formatIssues));
	}

	CitationCheck validateCitations(String answer, SearchResponse search) {
		List<String> cited = CitationParser.parse(answer);
		List<String> retrieved = search.hits().stream().map(SearchHit::clauseRef).toList();
		List<String> unknown = cited.stream().filter(ref -> !retrieved.contains(ref)).toList();
		Set<String> existing = unknown.isEmpty() ? Set.of()
				: this.clauseMapper.findByRefs(unknown).stream().map(KbClause::clauseRef).collect(Collectors.toSet());
		return new CitationValidator(existing::contains).validate(cited, retrieved);
	}

	static String formatClauses(List<SearchHit> hits) {
		StringBuilder out = new StringBuilder();
		for (SearchHit hit : hits) {
			out.append('[').append(hit.clauseRef()).append("] ");
			if (hit.path() != null && !hit.path().isEmpty()) {
				out.append(hit.path()).append(" > ");
			}
			out.append(hit.title()).append('\n').append(hit.snippet()).append("\n\n");
		}
		return out.toString().strip();
	}

	private static Double topScore(SearchHit hit, SearchMode mode) {
		return switch (mode) {
			case HYBRID_RERANK -> hit.rerankScore();
			case HYBRID -> hit.rrfScore();
			case BM25 -> hit.bm25Score();
			case DENSE -> hit.denseScore();
		};
	}

	private Map<String, Object> meta(PreparedQuestion prepared) {
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("mode", prepared.search().mode().value());
		meta.put("refuseThreshold", this.retrievalProperties.refuseThreshold());
		meta.put("topScore", prepared.topScore());
		List<Map<String, Object>> hits = new ArrayList<>();
		for (SearchHit hit : prepared.search().hits()) {
			Map<String, Object> h = new LinkedHashMap<>();
			h.put("clauseRef", hit.clauseRef());
			h.put("title", hit.title());
			h.put("path", hit.path());
			h.put("score", topScore(hit, prepared.search().mode()));
			hits.add(h);
		}
		meta.put("hits", hits);
		meta.put("timings", prepared.search().timings());
		return meta;
	}

	private static Map<String, Object> citationsEvent(CitationCheck check, SearchResponse search) {
		Map<String, SearchHit> byRef = search.hits()
			.stream()
			.collect(Collectors.toMap(SearchHit::clauseRef, h -> h, (a, b) -> a));
		List<Map<String, Object>> valid = new ArrayList<>();
		for (String ref : check.valid()) {
			SearchHit hit = byRef.get(ref);
			valid.add(Map.of("clauseRef", ref, "title", hit.title()));
		}
		List<Map<String, Object>> invalid = check.invalid()
			.stream()
			.map(i -> Map.<String, Object>of("clauseRef", i.clauseRef(), "reason", i.reason().name()))
			.toList();
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("valid", valid);
		event.put("invalid", invalid);
		event.put("invalidCount", check.invalidCount());
		return event;
	}

	private static Map<String, Object> done(long qaRecordId, boolean refused, long start, Long firstTokenMs,
			Usage usage, String model, List<String> formatIssues) {
		Map<String, Object> done = new LinkedHashMap<>();
		done.put("qaRecordId", qaRecordId);
		done.put("refused", refused);
		done.put("latencyMs", millisSince(start));
		done.put("firstTokenMs", firstTokenMs);
		done.put("model", model);
		done.put("inputTokens", (usage == null) ? null : usage.getPromptTokens());
		done.put("outputTokens", (usage == null) ? null : usage.getCompletionTokens());
		// 提示词要求纯文本；混入的 Markdown 标记种类（heading、bold、bullet…），空表示格式合规
		done.put("formatIssues", formatIssues);
		return done;
	}

	private long recordCall(String model, LlmCallStatus status, String errorCode, long start, Usage usage) {
		return this.llmCallRecorder.record(new LlmCall("qa", model, this.properties.promptVersion(),
				(int) millisSince(start), (usage == null) ? null : usage.getPromptTokens(),
				(usage == null) ? null : usage.getCompletionTokens(), null, status, errorCode, Instant.now()));
	}

	private long saveRecord(PreparedQuestion prepared, String answer, CitationCheck check, Long llmCallId,
			Long firstTokenMs, long start) {
		Map<String, Object> citations = new LinkedHashMap<>();
		citations.put("valid", check.valid());
		citations.put("invalid", check.invalid());
		com.cryptoassess.common.db.GeneratedKey key = new com.cryptoassess.common.db.GeneratedKey();
		this.qaRecordMapper.insert(new QaRecord(prepared.request().question(), prepared.search().mode().value(), answer,
				JSON.writeValueAsString(citations), check.invalidCount(), prepared.refused(), prepared.topScore(),
				prepared.refused() ? null : this.properties.promptVersion(), llmCallId,
				(firstTokenMs == null) ? null : firstTokenMs.intValue(), (int) millisSince(start), Instant.now()),
				key);
		return key.getId();
	}

	private static String text(ChatResponse chunk) {
		if (chunk.getResult() == null || chunk.getResult().getOutput() == null) {
			return null;
		}
		return chunk.getResult().getOutput().getText();
	}

	private static void sendUnchecked(QaEventSink sink, String event, Object data) {
		try {
			sink.send(event, data);
		}
		catch (IOException ex) {
			// 客户端断开：包装后中断流，让外层停止消费模型输出
			throw new SinkFailure(ex);
		}
	}

	private static long millisSince(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

	/** 写 SSE 失败（通常是客户端断开）。 */
	static final class SinkFailure extends RuntimeException {

		SinkFailure(IOException cause) {
			super(cause);
		}

		@Override
		public synchronized IOException getCause() {
			return (IOException) super.getCause();
		}

	}

}
