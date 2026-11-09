package com.opinio.passform.controller;

import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.SeedForms.Seeded;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 사람 결정(2026-11-09) 결과의 단답·장문 답 목록: 결과 통계와 같은 응답(submitted, 삭제 안 됨, 이 폼)만 들어간다.
// 실제 Postgres와 data/seed의 form_b(q4 자기소개 장문, 제출 14건)로 본다.
@Transactional
class ResultTextAnswersApiTest extends DatabaseTest {

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

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID ownerId;

	private Seeded formB;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		formB = SeedForms.load("form_b_mixed.json", ownerId, forms, formVersions, responses);
	}

	private JsonNode results() throws Exception {
		String body = mockMvc.perform(get("/forms/" + formB.formId() + "/results").with(user(ownerId.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonMapper.builder().build().readTree(body);
	}

	private Response save(ResponseStatus status, String motive) {
		return responses.saveAndFlush(new Response(formB.formId(), null, 3, status, List.of(new Answer("q4", motive)),
				false, NOW, status == ResponseStatus.SUBMITTED ? NOW : null));
	}

	private static List<String> values(JsonNode answers, String field) {
		List<String> values = new ArrayList<>();
		answers.forEach(answer -> values.add(answer.get(field).asString()));
		return values;
	}

	@Test
	void 작성_중_삭제된_다른_폼_응답의_답은_빠진다() throws Exception {
		save(ResponseStatus.IN_PROGRESS, "작성 중인 답");
		Response deleted = save(ResponseStatus.SUBMITTED, "삭제된 응답의 답");
		jdbc.update("update responses set creator_deleted_at = ? where response_id = ?", Timestamp.from(NOW),
				deleted.getId());
		Seeded otherForm = SeedForms.load("form_b_mixed.json", ownerId, forms, formVersions, responses);

		JsonNode answers = results().get("combined").get("text_questions").get(0).get("answers");

		assertThat(answers).hasSize(14);
		assertThat(values(answers, "value")).doesNotContain("작성 중인 답", "삭제된 응답의 답");
		Set<String> otherIds = otherForm.responseIds().values().stream().map(UUID::toString).collect(Collectors.toSet());
		assertThat(values(answers, "response_id")).doesNotContainAnyElementsOf(otherIds)
			.doesNotContain(deleted.getId().toString());
	}

	@Test
	void 통합은_최신_제출이_위이고_버전_탭은_그_버전_답만이다() throws Exception {
		JsonNode body = results();

		List<String> submittedAt = values(body.get("combined").get("text_questions").get(0).get("answers"),
				"submitted_at");
		assertThat(submittedAt).first().isEqualTo("2026-10-24T04:00:00Z");
		assertThat(submittedAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());
		JsonNode version3 = body.get("versions").get(2);
		assertThat(version3.get("form_version").asInt()).isEqualTo(3);
		assertThat(values(version3.get("text_questions").get(0).get("answers"), "response_id")).containsExactly(
				formB.response("rb04").toString(), formB.response("rb12").toString(), formB.response("rb13").toString());
	}

}
