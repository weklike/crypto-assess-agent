package com.cryptoassess.knowledge;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import com.cryptoassess.knowledge.format.LintIssue;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.knowledge.format.NormalizedFormatException;
import com.cryptoassess.support.WebSecurityTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = KnowledgeController.class, properties = WebSecurityTestConfiguration.PROPERTY)
@Import(WebSecurityTestConfiguration.class)
class KnowledgeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private KnowledgeIngestService ingestService;

	@MockitoBean
	private ClauseIndexer clauseIndexer;

	@Test
	void importReturns201WhenCreated() throws Exception {
		given(ingestService.importDocument("mock-standard.md"))
			.willReturn(new ImportResult(7L, "FIX/T 0001-2026", 36, true, "a".repeat(64)));

		mockMvc.perform(post("/api/kb/documents").header("X-API-Key", WebSecurityTestConfiguration.ADMIN_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fileName\":\"mock-standard.md\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.documentId").value(7))
			.andExpect(jsonPath("$.clauseCount").value(36));
	}

	@Test
	void importReturns200WhenAlreadyImported() throws Exception {
		given(ingestService.importDocument("mock-standard.md"))
			.willReturn(new ImportResult(7L, "FIX/T 0001-2026", 36, false, "a".repeat(64)));

		mockMvc.perform(post("/api/kb/documents").header("X-API-Key", WebSecurityTestConfiguration.ADMIN_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fileName\":\"mock-standard.md\"}")).andExpect(status().isOk());
	}

	@Test
	void pathTraversalIsRejected() throws Exception {
		mockMvc.perform(post("/api/kb/documents").header("X-API-Key", WebSecurityTestConfiguration.ADMIN_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fileName\":\"../raw/secret.md\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").exists());
	}

	@Test
	void formatErrorsComeBackAsProblemDetailWithLineNumbers() throws Exception {
		given(ingestService.importDocument(anyString()))
			.willThrow(new NormalizedFormatException(List.of(new LintIssue(9, "同级编号不连续"))));

		mockMvc.perform(post("/api/kb/documents").header("X-API-Key", WebSecurityTestConfiguration.ADMIN_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fileName\":\"broken.md\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:crypto-assess:error:normalized-format"))
			.andExpect(jsonPath("$.issues[0].line").value(9));
	}

	@Test
	void importWithoutAdminKeyIs401() throws Exception {
		mockMvc.perform(post("/api/kb/documents").contentType(MediaType.APPLICATION_JSON)
			.content("{\"fileName\":\"mock-standard.md\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:crypto-assess:error:unauthorized"));
	}

	@Test
	void unknownClauseRefIs404() throws Exception {
		given(ingestService.findClause("FIX/T 0001-2026#9.9")).willReturn(Optional.empty());

		mockMvc.perform(get("/api/kb/clauses").param("ref", "FIX/T 0001-2026#9.9"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404));
	}

}
