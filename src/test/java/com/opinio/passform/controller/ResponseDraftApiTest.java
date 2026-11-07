package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

// 응답 초안 API(SPEC 5절 189~191행, AC17 초안 부분, AC82, AC83)를 실제 Postgres와 data/seed의 form_required_branch로 시험한다.
// fixture: 버전 1·2, 최신 2, 제출 응답 rr01~rr03(3건). q1 "아니요"면 s2(q2 날짜·q3)를 건너뛴다. 인증은 모의 인증이다.
// fixture의 마감(2026-11-30)이 지나도 깨지지 않게 넣은 뒤 마감을 지운다. 마감 시험은 status로 닫는다.
@Transactional
class ResponseDraftApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

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

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID respondentId;

	private UUID otherId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		respondentId = users.saveAndFlush(TestData.newUser()).getId();
		otherId = users.saveAndFlush(TestData.newUser()).getId();
		formId = SeedForms.load("form_required_branch.json", ownerId, forms, formVersions, responses).formId();
		Form form = forms.findById(formId).orElseThrow();
		ReflectionTestUtils.setField(form, "deadline", null);
		forms.saveAndFlush(form);
	}

	private String draftPath() {
		return "/me/forms/" + formId + "/response-draft";
	}

	private static String body(Object value) {
		return JSON.writeValueAsString(value);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object value) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(value));
	}

	private ResultActions putDraft(UUID userId, int version, List<Map<String, Object>> answers) throws Exception {
		return mockMvc.perform(json(put(draftPath()), Map.of("form_version", version, "answers", answers))
			.with(user(userId.toString())));
	}

	private ResultActions getDraft(UUID userId) throws Exception {
		return mockMvc.perform(get(draftPath()).with(user(userId.toString())));
	}

	private ResultActions submit(UUID userId, Map<String, Object> request) throws Exception {
		MockHttpServletRequestBuilder builder = json(post("/forms/" + formId + "/responses"), request);
		return mockMvc.perform(userId == null ? builder : builder.with(user(userId.toString())));
	}

	private static Map<String, Object> answer(String questionId, Object value) {
		return Map.of("question_id", questionId, "value", value);
	}

	private static String responseId(MvcResult result) throws Exception {
		return JSON.readTree(result.getResponse().getContentAsString()).get("response_id").asString();
	}

	private String saveFirstDraft() throws Exception {
		return responseId(putDraft(respondentId, 2, List.of(answer("q1", "예"))).andExpect(status().isCreated())
			.andReturn());
	}

	private Map<String, Object> row(String responseId) {
		entityManager.flush();
		entityManager.clear();
		return jdbc.queryForMap("select status, form_version, user_id, submitted_at, updated_at from responses"
				+ " where response_id = ?::uuid", responseId);
	}

	private int draftRows(UUID userId) {
		entityManager.flush();
		return jdbc.queryForObject(
				"select count(*) from responses where user_id = ? and form_id = ? and status = 'IN_PROGRESS'",
				Integer.class, userId, formId);
	}

	private int resultCount() throws Exception {
		MvcResult result = mockMvc.perform(get("/forms/" + formId + "/results").with(user(ownerId.toString())))
			.andExpect(status().isOk())
			.andReturn();
		return JSON.readTree(result.getResponse().getContentAsString()).get("response_count").asInt();
	}

	private void publishVersion3() {
		Map<String, Object> v2 = formVersions.findAll().stream()
			.filter(v -> v.getFormId().equals(formId) && v.getVersion() == 2)
			.findFirst()
			.orElseThrow()
			.getDefinition();
		Map<String, Object> v3 = new java.util.LinkedHashMap<>(v2);
		v3.put("title", "행사 참석 신청 (버전 3)");
		formVersions.saveAndFlush(new FormVersion(formId, 3, v3, TestData.NOW));
		Form form = forms.findById(formId).orElseThrow();
		ReflectionTestUtils.setField(form, "definitionVersion", 3);
		forms.saveAndFlush(form);
	}

	// AC83 in_progress_owner_only_and_not_counted
	@Test
	void AC83_미완성_초안은_본인만_조회하고_결과와_분석_대상에_들지_않는다() throws Exception {
		String draftId = responseId(putDraft(respondentId, 2, List.of(answer("q1", "예"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("in_progress"))
			.andExpect(jsonPath("$.saved_at").isNotEmpty())
			.andReturn());

		getDraft(respondentId).andExpect(status().isOk())
			.andExpect(jsonPath("$.response_id").value(draftId))
			.andExpect(jsonPath("$.form_version").value(2))
			.andExpect(jsonPath("$.answers[0].question_id").value("q1"))
			.andExpect(jsonPath("$.answers[0].value").value("예"))
			.andExpect(jsonPath("$.form_definition.definition_version").value(2))
			.andExpect(jsonPath("$.form_definition.sections[0].questions[0].question_id").value("q1"));
		getDraft(otherId).andExpect(status().isNotFound());
		getDraft(ownerId).andExpect(status().isNotFound());

		assertThat(resultCount()).isEqualTo(3);
		assertThat(responses.findSubmittedNotDeletedByFormId(formId)).hasSize(3)
			.noneMatch(response -> response.getId().toString().equals(draftId));
		Map<String, Object> stored = row(draftId);
		assertThat(stored.get("status")).isEqualTo("IN_PROGRESS");
		assertThat(stored.get("submitted_at")).isNull();
		assertThat(stored.get("updated_at")).isNull();
	}

	@Test
	void 두_번째_저장은_같은_초안의_답을_바꾸고_200이다() throws Exception {
		String draftId = saveFirstDraft();

		String again = responseId(putDraft(respondentId, 2, List.of(answer("q1", "아니요"), answer("q4", "동아리")))
			.andExpect(status().isOk())
			.andReturn());

		assertThat(again).isEqualTo(draftId);
		assertThat(draftRows(respondentId)).isEqualTo(1);
		getDraft(respondentId).andExpect(jsonPath("$.answers.length()").value(2))
			.andExpect(jsonPath("$.answers[0].value").value("아니요"));
	}

	// AC17 "작성 중 답 첫 자동 저장 → saved, has_draft: true". GET /me/forms는 E5라 행과 근거를 본다.
	@Test
	void AC17_첫_자동_저장은_내_폼함에_saved로_한_번_넣는다() throws Exception {
		saveFirstDraft();
		entityManager.flush();
		Map<String, Object> saved = jdbc.queryForMap("select state, saved_at from saved_forms where user_id = ? and form_id = ?",
				respondentId, formId);
		assertThat(saved.get("state")).isEqualTo("SAVED");
		assertThat(responses.hasDraft(respondentId, formId)).isTrue();

		putDraft(respondentId, 2, List.of(answer("q1", "아니요"))).andExpect(status().isOk());
		entityManager.flush();
		assertThat(jdbc.queryForObject("select count(*) from saved_forms where user_id = ? and form_id = ?",
				Integer.class, respondentId, formId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select saved_at from saved_forms where user_id = ? and form_id = ?",
				java.sql.Timestamp.class, respondentId, formId)).isEqualTo(saved.get("saved_at"));
		assertThat(responses.hasDraft(otherId, formId)).isFalse();
	}

	// SPEC 5절 207행: 초안에는 필수·형식·분기 검사를 하지 않는다. 동의로 처리하지도 않는다.
	@Test
	void 초안은_미완성_답과_형식에_맞지_않는_값도_저장한다() throws Exception {
		putDraft(respondentId, 2, List.of(answer("q2", "2026-02-30"), answer("q3", "선택지에 없음")))
			.andExpect(status().isCreated());
		putDraft(respondentId, 2, List.of()).andExpect(status().isOk());
	}

	@Test
	void 값_모양이_문자열_배열_null이_아니면_400이다() throws Exception {
		putDraft(respondentId, 2, List.of(answer("q1", 3))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));
		putDraft(respondentId, 2, List.of(answer("q1", List.of(1)))).andExpect(status().isBadRequest());
		assertThat(draftRows(respondentId)).isZero();
	}

	// AC83: 동의 없는 제출 400 뒤 초안 유지
	@Test
	void AC83_동의_없는_제출은_400이고_초안을_유지한다() throws Exception {
		String draftId = saveFirstDraft();

		submit(respondentId, Map.of("form_version", 2, "answers", List.of(answer("q1", "아니요"), answer("q4", "동아리")),
				"consent_agreed", false, "draft_response_id", draftId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("consent_required"));

		assertThat(row(draftId).get("status")).isEqualTo("IN_PROGRESS");
		assertThat(resultCount()).isEqualTo(3);
	}

	@Test
	void 제출_검증_실패에도_초안을_유지한다() throws Exception {
		String draftId = saveFirstDraft();

		submit(respondentId, Map.of("form_version", 2, "answers", List.of(answer("q1", "아니요")), "consent_agreed", true,
				"draft_response_id", draftId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("required_missing"));

		assertThat(row(draftId).get("status")).isEqualTo("IN_PROGRESS");
	}

	// AC83 submit_draft_same_response_id
	@Test
	void AC83_초안을_제출하면_같은_ID가_submitted가_되고_응답_수가_1_는다() throws Exception {
		String draftId = saveFirstDraft();

		MvcResult result = submit(respondentId, Map.of("form_version", 2,
				"answers", List.of(answer("q1", "아니요"), answer("q4", "동아리")), "consent_agreed", true,
				"draft_response_id", draftId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("submitted"))
			.andReturn();

		assertThat(responseId(result)).isEqualTo(draftId);
		Map<String, Object> stored = row(draftId);
		assertThat(stored.get("status")).isEqualTo("SUBMITTED");
		assertThat(stored.get("submitted_at")).isNotNull();
		assertThat(stored.get("user_id")).isEqualTo(respondentId);
		assertThat(draftRows(respondentId)).isZero();
		assertThat(resultCount()).isEqualTo(4);
		getDraft(respondentId).andExpect(status().isNotFound());
	}

	// AC83: 다른 사용자의 draft_response_id 제출 거부. 비로그인도 초안 ID를 쓸 수 없다.
	@Test
	void AC83_다른_사용자와_비로그인의_draft_response_id는_404이고_초안은_그대로다() throws Exception {
		String draftId = saveFirstDraft();
		Map<String, Object> request = Map.of("form_version", 2,
				"answers", List.of(answer("q1", "아니요"), answer("q4", "동아리")), "consent_agreed", true,
				"draft_response_id", draftId);

		submit(otherId, request).andExpect(status().isNotFound());
		submit(null, request).andExpect(status().isNotFound());

		assertThat(row(draftId).get("status")).isEqualTo("IN_PROGRESS");
		assertThat(row(draftId).get("user_id")).isEqualTo(respondentId);
		assertThat(resultCount()).isEqualTo(3);
	}

	@Test
	void 제출된_응답의_ID는_초안으로_다시_제출할_수_없다() throws Exception {
		String draftId = saveFirstDraft();
		Map<String, Object> request = Map.of("form_version", 2,
				"answers", List.of(answer("q1", "아니요"), answer("q4", "동아리")), "consent_agreed", true,
				"draft_response_id", draftId);
		submit(respondentId, request).andExpect(status().isCreated());

		submit(respondentId, request).andExpect(status().isNotFound());
		assertThat(resultCount()).isEqualTo(4);
	}

	// AC83 stale_draft_not_submitted: 새 배포 뒤 옛 초안은 기존 문항과 함께 보여 주되 그대로 제출할 수 없다.
	@Test
	void AC83_새_버전이_배포되면_옛_초안은_보이되_저장도_제출도_409다() throws Exception {
		String draftId = saveFirstDraft();
		publishVersion3();

		getDraft(respondentId).andExpect(status().isOk())
			.andExpect(jsonPath("$.form_version").value(2))
			.andExpect(jsonPath("$.form_definition.definition_version").value(2))
			.andExpect(jsonPath("$.form_definition.title").value("행사 참석 신청"));
		putDraft(respondentId, 2, List.of(answer("q1", "아니요"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("form_version_changed"));
		putDraft(respondentId, 3, List.of(answer("q1", "아니요"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("form_version_changed"));
		for (int version : new int[] { 2, 3 }) {
			submit(respondentId, Map.of("form_version", version,
					"answers", List.of(answer("q1", "아니요"), answer("q4", "동아리")), "consent_agreed", true,
					"draft_response_id", draftId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("form_version_changed"));
		}
		assertThat(row(draftId).get("status")).isEqualTo("IN_PROGRESS");
		assertThat(row(draftId).get("form_version")).isEqualTo(2);
		assertThat(resultCount()).isEqualTo(3);

		// 옛 초안을 지우면 최신 버전으로 새로 시작할 수 있다(SPEC 5절 208행).
		mockMvc.perform(delete(draftPath()).with(csrf()).with(user(respondentId.toString())))
			.andExpect(status().isNoContent());
		putDraft(respondentId, 3, List.of(answer("q1", "예"))).andExpect(status().isCreated());
	}

	// SPEC 5절 208행: 닫히면 초안은 보존하되 저장·제출은 막는다.
	@Test
	void 닫힌_폼은_초안을_보존하고_저장은_409_form_closed다() throws Exception {
		String draftId = saveFirstDraft();
		Form form = forms.findById(formId).orElseThrow();
		ReflectionTestUtils.setField(form, "status", FormStatus.CLOSED);
		forms.saveAndFlush(form);

		putDraft(respondentId, 2, List.of(answer("q1", "아니요"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("form_closed"));
		getDraft(respondentId).andExpect(status().isOk()).andExpect(jsonPath("$.answers[0].value").value("예"));
		assertThat(row(draftId).get("status")).isEqualTo("IN_PROGRESS");
	}

	@Test
	void 삭제는_본인_초안만_지우고_없어도_204다() throws Exception {
		String draftId = saveFirstDraft();

		mockMvc.perform(delete(draftPath()).with(csrf()).with(user(otherId.toString())))
			.andExpect(status().isNoContent());
		assertThat(row(draftId).get("status")).isEqualTo("IN_PROGRESS");

		mockMvc.perform(delete(draftPath()).with(csrf()).with(user(respondentId.toString())))
			.andExpect(status().isNoContent());
		getDraft(respondentId).andExpect(status().isNotFound());
		mockMvc.perform(delete(draftPath()).with(csrf()).with(user(respondentId.toString())))
			.andExpect(status().isNoContent());
		assertThat(resultCount()).isEqualTo(3);
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get(draftPath())).andExpect(status().isUnauthorized());
		mockMvc.perform(json(put(draftPath()), Map.of("form_version", 2, "answers", List.of())))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(draftPath()).with(csrf())).andExpect(status().isUnauthorized());
		mockMvc.perform(get(draftPath()).with(user(UUID.randomUUID().toString()))).andExpect(status().isUnauthorized());
	}

	@Test
	void 배포_전_폼과_없는_폼은_404다() throws Exception {
		UUID draftForm = forms.saveAndFlush(new Form(ownerId, Map.of("title", "초안"), TestData.NOW)).getId();
		mockMvc.perform(json(put("/me/forms/" + draftForm + "/response-draft"),
				Map.of("form_version", 1, "answers", List.of())).with(user(respondentId.toString())))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/me/forms/not-a-uuid/response-draft").with(user(respondentId.toString())))
			.andExpect(status().isNotFound());
	}

}
