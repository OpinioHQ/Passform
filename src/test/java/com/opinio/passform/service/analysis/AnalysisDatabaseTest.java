package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

// 소유·없는 폼·인증을 실제 Postgres와 보안 필터로 시험한다(AC2). 파서를 거치지 않게 {context}로 요청한다.
class AnalysisDatabaseTest extends DatabaseTest {

	private static final String COUNT_ALL = "{\"context\":{\"conditions\":[],\"operation\":\"count\"}}";

	private static final Map<String, Object> DEFINITION = Map.of("sections",
			List.of(Map.of("section_id", "s1", "questions",
					List.of(Map.of("question_id", "q1", "question_note", "소감", "question_type", "short_answer")))));

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository formVersions;

	@Autowired
	private ResponseRepository responses;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID otherId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		ownerId = users.save(TestData.newUser()).getId();
		otherId = users.save(TestData.newUser()).getId();
		formId = forms.save(new Form(ownerId, DEFINITION, TestData.NOW)).getId();
		formVersions.save(new FormVersion(formId, 1, DEFINITION, TestData.NOW));
		responses.save(TestData.submitted(formId, otherId, 1, TestData.NOW));
	}

	private int statusOf(UUID userId, String path, String body) throws Exception {
		return mockMvc
			.perform(post(path).with(user(userId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	@Test
	void 소유자의_context_요청은_결과를_API_이름으로_돌려준다() throws Exception {
		mockMvc
			.perform(post("/forms/" + formId + "/analyze").with(user(ownerId.toString()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(COUNT_ALL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.result.count").value(1))
			.andExpect(jsonPath("$.result.response_ids").isEmpty())
			.andExpect(jsonPath("$.coverage.total_submitted_count").value(1))
			.andExpect(jsonPath("$.clarify").isEmpty());
	}

	@Test
	void 다른_사용자는_유효한_토큰이_있어도_403이고_내용이_없다() throws Exception {
		String body = mockMvc
			.perform(post("/forms/" + formId + "/analyze").with(user(otherId.toString()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(COUNT_ALL))
			.andExpect(status().isForbidden())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(body).isEqualTo("{\"error\":\"forbidden\"}");
	}

	@Test
	void 소유_확인이_본문_판정보다_먼저다() throws Exception {
		assertThat(statusOf(otherId, "/forms/" + formId + "/analyze", "{깨진 본문")).isEqualTo(403);
		assertThat(statusOf(ownerId, "/forms/" + formId + "/analyze", "{깨진 본문")).isEqualTo(400);
	}

	@Test
	void 없는_폼과_UUID가_아닌_ID와_삭제된_폼은_404다() throws Exception {
		assertThat(statusOf(ownerId, "/forms/" + UUID.randomUUID() + "/analyze", COUNT_ALL)).isEqualTo(404);
		assertThat(statusOf(ownerId, "/forms/form_b/analyze", COUNT_ALL)).isEqualTo(404);

		Form deleted = forms.findById(formId).orElseThrow();
		ReflectionTestUtils.setField(deleted, "deletedAt", TestData.NOW);
		forms.save(deleted);
		assertThat(statusOf(ownerId, "/forms/" + formId + "/analyze", COUNT_ALL)).isEqualTo(404);
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc
			.perform(post("/forms/" + formId + "/analyze").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(COUNT_ALL))
			.andExpect(status().isUnauthorized());
	}

}
