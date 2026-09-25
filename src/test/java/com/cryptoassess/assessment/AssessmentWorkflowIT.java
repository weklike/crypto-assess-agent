package com.cryptoassess.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cryptoassess.TestcontainersConfiguration;
import com.cryptoassess.knowledge.KnowledgeIngestService;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.support.ElasticsearchTestcontainers;
import com.cryptoassess.support.FakeAiConfiguration;
import com.cryptoassess.support.FakeChatModel;
import com.cryptoassess.support.JudgeOutputs;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = { "app.knowledge.normalized-dir=data/fixtures", "app.workflow.resume-on-startup=false" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ TestcontainersConfiguration.class, ElasticsearchTestcontainers.class, FakeAiConfiguration.class,
		FakeRerankConfiguration.class })
class AssessmentWorkflowIT {

	private static final Pattern TARGET = Pattern.compile("测评指标条款：\\s*\\[([^\\]]+)\\]");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakeChatModel chatModel;

	@Autowired
	private AssessmentService assessmentService;

	@Autowired
	private AnalysisRunner analysisRunner;

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
	void resetModel() {
		chatModel.reset();
	}

	/** 回复里引用提示词中的目标条款（judge-output.v2 格式）；第 failAt 次调用返回非法输出。 */
	private void respond(String judgment, int failAt) {
		chatModel.reset();
		AtomicInteger n = new AtomicInteger();
		chatModel.setResponder(prompt -> {
			if (n.incrementAndGet() == failAt) {
				return FakeChatModel.reply("无法判定");
			}
			Matcher m = TARGET.matcher(prompt);
			String ref = m.find() ? m.group(1) : "none";
			return FakeChatModel.reply(JudgeOutputs.of(judgment, ref));
		});
	}

	private long projectWithTwoObjects() {
		AssessProject project = assessmentService
			.create(new AssessmentRequests.CreateAssessment("自查", "营销系统", 3));
		assessmentService.addObject(project.id(), new AssessmentRequests.AddObject("应用和数据", "业务数据库", "保存客户信息",
				JSON.readTree("{\"storage\":[{\"algorithm\":\"SM4-CBC\",\"evidence\":\"字段加密\"}]}")));
		assessmentService.addObject(project.id(), new AssessmentRequests.AddObject("网络和通信", "SSL VPN", "远程接入",
				JSON.readTree("{\"transport\":[{\"protocol\":\"国密 TLS\",\"algorithm\":\"SM4\"}]}")));
		return project.id();
	}

	private AssessmentStatus awaitSettled(long id) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (System.nanoTime() < deadline) {
			AssessmentStatus status = assessmentService.project(id).status();
			if (status != AssessmentStatus.ANALYZING) {
				return status;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("analysis did not finish");
	}

	private int count(String sql, long id) {
		return jdbcTemplate.queryForObject(sql, Integer.class, id);
	}

	@Test
	void failureOnSecondObjectThenResumeOnlyRunsRemainingSteps() throws Exception {
		long id = projectWithTwoObjects();
		// 对象 1（应用和数据，第三级）有 7 条指标；第 8 次调用是对象 2 的第一条，返回非法输出
		respond("部分符合", 8);

		mockMvc.perform(post("/api/assessments/{id}/analyze", id)).andExpect(status().isAccepted());
		assertThat(awaitSettled(id)).isEqualTo(AssessmentStatus.FAILED);
		assertThat(chatModel.calls()).isEqualTo(8);
		assertThat(assessmentService.project(id).lastError()).contains("JUDGE");
		assertThat(count("SELECT COUNT(*) FROM assess_finding WHERE project_id = ?", id)).isEqualTo(7);
		assertThat(count("SELECT COUNT(*) FROM assess_step WHERE project_id = ? AND status = 'FAILED'", id)).isEqualTo(1);

		respond("部分符合", -1);
		mockMvc.perform(post("/api/assessments/{id}/analyze", id)).andExpect(status().isAccepted());
		assertThat(awaitSettled(id)).isEqualTo(AssessmentStatus.REVIEW);
		// 对象 2（网络和通信，第三级）有 5 条指标；已成功的 7 条不再调用模型
		assertThat(chatModel.calls()).isEqualTo(5);
		assertThat(count("SELECT COUNT(*) FROM assess_finding WHERE project_id = ?", id)).isEqualTo(12);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT attempts FROM assess_step WHERE project_id = ? AND status = 'SUCCEEDED' AND step = 'JUDGE' "
						+ "AND step_key = 'FIX/T 0001-2026#5.2.1'",
				Integer.class, id)).isEqualTo(2);
	}

	@Test
	void interruptedAnalysisResumesAfterRestart() throws Exception {
		long id = projectWithTwoObjects();
		respond("符合", -1);
		analysisRunner.startAnalysis(id);
		assertThat(awaitSettled(id)).isEqualTo(AssessmentStatus.REVIEW);
		int firstRunCalls = chatModel.calls();

		// 模拟进程在分析途中被杀：状态停在 ANALYZING，最后一步停在 RUNNING，对应的差距项没写入
		jdbcTemplate.update("UPDATE assess_project SET status = 'ANALYZING' WHERE id = ?", id);
		jdbcTemplate.update("UPDATE assess_step SET status = 'RUNNING' WHERE project_id = ? AND step_key = ?", id,
				"FIX/T 0001-2026#5.2.5");
		jdbcTemplate.update("DELETE FROM assess_finding WHERE project_id = ? AND clause_ref = ?", id,
				"FIX/T 0001-2026#5.2.5");
		respond("符合", -1);

		analysisRunner.recoverInterrupted();

		assertThat(awaitSettled(id)).isEqualTo(AssessmentStatus.REVIEW);
		assertThat(firstRunCalls).isEqualTo(12);
		assertThat(chatModel.calls()).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM assess_finding WHERE project_id = ?", id)).isEqualTo(12);
	}

	@Test
	void reviewAllFindingsThenConfirm() throws Exception {
		long id = projectWithTwoObjects();
		respond("部分符合", -1);
		analysisRunner.startAnalysis(id);
		assertThat(awaitSettled(id)).isEqualTo(AssessmentStatus.REVIEW);
		List<Long> findings = jdbcTemplate.queryForList("SELECT id FROM assess_finding WHERE project_id = ? ORDER BY id",
				Long.class, id);

		mockMvc.perform(post("/api/assessments/{id}/confirm", id))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("12")));

		mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", id, findings.get(0))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"judgment\":\"不符合\",\"note\":\"现场核查未见加密配置\",\"reviewed\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgment").value("不符合"))
			.andExpect(jsonPath("$.source").value("REVIEWER"))
			.andExpect(jsonPath("$.reviewed").value(true));
		mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", id, findings.get(1))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"judgment\":\"基本符合\"}")).andExpect(status().isBadRequest());
		// 技术层面的“部分符合”必须给出 D/A/K；给出后判定由维度推出，Ra 由复核人员补充
		mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", id, findings.get(1))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"judgment\":\"部分符合\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", id, findings.get(1))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"d\":true,\"a\":false,\"k\":true,\"ra\":0.5,\"note\":\"使用 AES\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgment").value("部分符合"))
			.andExpect(jsonPath("$.dimA").value(false))
			.andExpect(jsonPath("$.ra").value(0.5));
		for (Long fid : findings.subList(1, findings.size())) {
			mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", id, fid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reviewed\":true}")).andExpect(status().isOk());
		}

		mockMvc.perform(post("/api/assessments/{id}/confirm", id)).andExpect(status().isOk());
		assertThat(assessmentService.project(id).status()).isEqualTo(AssessmentStatus.SCORED);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT rule_version FROM assess_score WHERE project_id = ? AND scope = 'total'", String.class, id))
			.isEqualTo("scoring.v2-test");
		// 两个对象都属于技术要求，管理组整组不适用：按约定不出总分，转人工
		assertThat(jdbcTemplate.queryForObject(
				"SELECT score FROM assess_score WHERE project_id = ? AND scope = 'total'", java.math.BigDecimal.class, id))
			.isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT detail_json FROM assess_score WHERE project_id = ? AND scope = 'total'", String.class, id))
			.contains("人工");
		assertThat(count("SELECT COUNT(*) FROM assess_score WHERE project_id = ? AND scope = 'layer' AND score IS NOT NULL",
				id)).isEqualTo(2);
		mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", id, findings.get(0))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reviewed\":false}")).andExpect(status().isConflict());

		// T17：导出报告草稿，内容全部来自数据库，不调用模型
		int callsBefore = chatModel.calls();
		byte[] docx = mockMvc.perform(get("/api/assessments/{id}/report.docx", id))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".docx")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(chatModel.calls()).isEqualTo(callsBefore);
		assertThat(assessmentService.project(id).status()).isEqualTo(AssessmentStatus.REPORTED);
		try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx))) {
			String text = doc.getParagraphs().stream().map(p -> p.getText()).collect(java.util.stream.Collectors.joining("\n"))
					+ doc.getTables().stream().map(t -> t.getText()).collect(java.util.stream.Collectors.joining("\n"));
			assertThat(text).contains("营销系统", "辅助自查草稿，需测评人员确认", "现场核查未见加密配置", "judge.v2",
					"scoring.v2-test", "不出总分");
		}
	}

	@Test
	void reportBeforeScoringIs409() throws Exception {
		long id = projectWithTwoObjects();

		mockMvc.perform(get("/api/assessments/{id}/report.docx", id)).andExpect(status().isConflict());
	}

	@Test
	void findingFromAnotherProjectIs404() throws Exception {
		long a = projectWithTwoObjects();
		respond("符合", -1);
		analysisRunner.startAnalysis(a);
		awaitSettled(a);
		long fid = jdbcTemplate.queryForObject("SELECT MIN(id) FROM assess_finding WHERE project_id = ?", Long.class, a);
		long b = assessmentService.create(new AssessmentRequests.CreateAssessment("另一个", "系统", 2)).id();

		mockMvc.perform(patch("/api/assessments/{id}/findings/{fid}", b, fid).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reviewed\":true}")).andExpect(status().isNotFound());
	}

}
