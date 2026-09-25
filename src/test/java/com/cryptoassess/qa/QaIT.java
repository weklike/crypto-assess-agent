package com.cryptoassess.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeChatModel;
import com.cryptoassess.support.FakeRerankClient;
import com.cryptoassess.support.FakeRerankConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = { "app.knowledge.normalized-dir=data/fixtures", "app.qa.timeout=2s",
		"app.retrieval.refuse-threshold=0.3" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class QaIT {

	private static final Pattern EVENT = Pattern.compile("event:(\\S+)\\ndata:(.*)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakeChatModel chatModel;

	@Autowired
	private FakeRerankClient rerankClient;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeAll
	void indexFixture(@Autowired KnowledgeIngestService ingestService, @Autowired ClauseIndexer indexer) {
		jdbcTemplate.update("DELETE FROM kb_clause");
		jdbcTemplate.update("DELETE FROM kb_document");
		ingestService.importDocument("mock-standard.md");
		indexer.reindex();
	}

	@AfterEach
	void reset() {
		chatModel.reset();
		rerankClient.setAvailable(true);
	}

	@Test
	void markdownInTheAnswerIsReportedInDone() throws Exception {
		chatModel.enqueue(FakeChatModel.reply("## 结论\n", "- **应加密**存储 [FIX/T 0001-2026#5.4.3]"));

		List<Event> events = ask("{\"question\":\"数据库中的敏感信息要加密存储吗\"}");

		assertThat(events.getLast().name()).isEqualTo("done");
		assertThat(events.getLast().data()).contains("\"formatIssues\":[\"heading\",\"bold\",\"bullet\"]");
	}

	@Test
	void streamsMetaTokensCitationsDoneAndStripsInvalidCitations() throws Exception {
		chatModel.enqueue(FakeChatModel.reply("数据库中的敏感信息应加密存储", "[FIX/T 0001-2026#5.4.3]；",
				"密钥不能与密文放在一起【FIX/T 0001-2026#9.9】。"));

		List<Event> events = ask("{\"question\":\"数据库中的敏感信息要加密存储吗\"}");

		assertThat(events).extracting(Event::name).first().isEqualTo("meta");
		assertThat(events).extracting(Event::name).last().isEqualTo("done");
		List<String> names = events.stream().map(Event::name).toList();
		assertThat(names.subList(1, names.size() - 2)).containsOnly("token").hasSize(3);
		assertThat(names.get(names.size() - 2)).isEqualTo("citations");
		Event citations = events.get(events.size() - 2);
		assertThat(citations.data()).contains("FIX/T 0001-2026#5.4.3").contains("\"invalidCount\":1")
			.contains("FIX/T 0001-2026#9.9");
		assertThat(events.getLast().data()).contains("\"refused\":false").contains("\"qaRecordId\"")
			.contains("\"formatIssues\":[]");

		assertThat(jdbcTemplate.queryForObject(
				"SELECT invalid_citation_count FROM qa_record ORDER BY id DESC LIMIT 1", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM llm_call ORDER BY id DESC LIMIT 1", String.class))
			.isEqualTo("OK");
		// 提示词里只放了检索到的条款，每条前带 [clause_ref]
		assertThat(chatModel.prompts().getFirst().getContents()).contains("[FIX/T 0001-2026#5.4.3]");
	}

	@Test
	void timeoutEmitsErrorEventAndRecordsTimeout() throws Exception {
		chatModel.enqueue(FakeChatModel.hang(Duration.ofSeconds(10)));

		List<Event> events = ask("{\"question\":\"数据库中的敏感信息要加密存储吗\"}");

		assertThat(events).extracting(Event::name).containsExactly("meta", "error");
		assertThat(events.getLast().data()).contains("llm-timeout").contains("504");
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM llm_call ORDER BY id DESC LIMIT 1", String.class))
			.isEqualTo("TIMEOUT");
	}

	@Test
	void emptyReplyIsAnExplicitError() throws Exception {
		chatModel.enqueue(FakeChatModel.empty());

		List<Event> events = ask("{\"question\":\"数据库中的敏感信息要加密存储吗\"}");

		assertThat(events).extracting(Event::name).containsExactly("meta", "error");
		assertThat(events.getLast().data()).contains("llm-invalid-output");
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM llm_call ORDER BY id DESC LIMIT 1", String.class))
			.isEqualTo("EMPTY");
	}

	@Test
	void lowRerankScoreRefusesWithoutCallingModel() throws Exception {
		List<Event> events = ask("{\"question\":\"今天午饭吃什么\"}");

		assertThat(chatModel.calls()).isZero();
		assertThat(events).extracting(Event::name).containsExactly("meta", "token", "citations", "done");
		assertThat(events.getLast().data()).contains("\"refused\":true");
		assertThat(jdbcTemplate.queryForObject("SELECT refused FROM qa_record ORDER BY id DESC LIMIT 1", Boolean.class))
			.isTrue();
	}

	@Test
	void rerankUnavailableFailsBeforeStreamingWith503() throws Exception {
		rerankClient.setAvailable(false);

		mockMvc.perform(post("/api/qa").contentType(MediaType.APPLICATION_JSON)
			.content("{\"question\":\"数据库中的敏感信息要加密存储吗\"}"))
			.andExpect(status().isServiceUnavailable());
		assertThat(chatModel.calls()).isZero();
	}

	@Test
	void blankQuestionIs400() throws Exception {
		mockMvc.perform(post("/api/qa").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
			.andExpect(status().isBadRequest());
	}

	private List<Event> ask(String body) throws Exception {
		MvcResult started = mockMvc
			.perform(post("/api/qa").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.content(body))
			.andExpect(request().asyncStarted())
			.andReturn();
		started.getAsyncResult(15_000);
		MvcResult result = mockMvc.perform(asyncDispatch(started)).andReturn();
		String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		List<Event> events = new ArrayList<>();
		Matcher matcher = EVENT.matcher(content);
		while (matcher.find()) {
			events.add(new Event(matcher.group(1), matcher.group(2)));
		}
		return events;
	}

	record Event(String name, String data) {
	}

}
