package com.cryptoassess.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cryptoassess.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssessmentApiIT {

	private static final String OBJECT = """
			{"layer":"应用和数据","name":"核心业务数据库","description":"MySQL 8，保存客户信息",
			 "measures":{"storage":[{"algorithm":"SM4-CBC","product":"数据库加密组件","evidence":"字段级加密配置"}],
			             "key_mgmt":[{"product":"服务器密码机"}]}}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long createProject() throws Exception {
		String body = mockMvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"2026 年度自查\",\"systemName\":\"营销业务系统\",\"level\":3}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	void createProjectAddObjectAndQuery() throws Exception {
		long id = createProject();

		mockMvc.perform(post("/api/assessments/{id}/objects", id).contentType(MediaType.APPLICATION_JSON).content(OBJECT))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("核心业务数据库"))
			.andExpect(jsonPath("$.measures.storage[0].algorithm").value("SM4-CBC"))
			.andExpect(jsonPath("$.measures.key_mgmt[0].product").value("服务器密码机"));

		mockMvc.perform(get("/api/assessments/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.project.status").value("READY"))
			.andExpect(jsonPath("$.project.level").value(3))
			.andExpect(jsonPath("$.objects.length()").value(1))
			.andExpect(jsonPath("$.objects[0].layer").value("应用和数据"))
			.andExpect(jsonPath("$.findings.length()").value(0))
			.andExpect(jsonPath("$.steps.length()").value(0));

		mockMvc.perform(get("/api/assessments")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
	}

	@Test
	void invalidInputIs400() throws Exception {
		mockMvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"x\",\"systemName\":\"y\",\"level\":5}")).andExpect(status().isBadRequest());
		long id = createProject();
		mockMvc.perform(post("/api/assessments/{id}/objects", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"layer\":\"应用安全\",\"name\":\"n\",\"measures\":{}}")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/assessments/{id}/objects", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"layer\":\"应用和数据\",\"name\":\"n\",\"measures\":{\"network\":[]}}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/assessments/{id}/objects", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"layer\":\"应用和数据\",\"name\":\"n\",\"measures\":{\"storage\":[{}]}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("storage[0]")));
	}

	@Test
	void unknownProjectIs404() throws Exception {
		mockMvc.perform(get("/api/assessments/{id}", 999_999)).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/assessments/{id}/objects", 999_999).contentType(MediaType.APPLICATION_JSON)
			.content(OBJECT)).andExpect(status().isNotFound());
	}

	@Test
	void addingObjectWhileAnalyzingIs409() throws Exception {
		long id = createProject();
		jdbcTemplate.update("UPDATE assess_project SET status = 'ANALYZING' WHERE id = ?", id);

		mockMvc.perform(post("/api/assessments/{id}/objects", id).contentType(MediaType.APPLICATION_JSON).content(OBJECT))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:crypto-assess:error:state-conflict"));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM assess_object WHERE project_id = ?",
				Integer.class, id)).isZero();
	}

	@Test
	void confirmBeforeReviewIs409() throws Exception {
		long id = createProject();

		mockMvc.perform(post("/api/assessments/{id}/confirm", id)).andExpect(status().isConflict());
	}

}
