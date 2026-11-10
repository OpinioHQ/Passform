package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;
import com.opinio.passform.domain.inbox.SavedFormState;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// PATCH·DELETE /me/responses/{response_id}를 실제 Postgres와 form_required_branch로 시험한다(AC2, AC16, AC17, AC53, AC73, AC74).
// SPEC 추적표의 골든 id는 golden_cases.yaml에 없어 일반 테스트로 하고 테스트 이름 앞에 적는다. 기대값은 구현 근거이며 판정 골든은 X2에서 사람이 쓴다.
// fixture: 버전 1·2, q1이 "아니요"면 s3으로 건너뛴다. q2 날짜·q3·q4는 필수다.
@Transactional
class ResponseChangeApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Instant SUBMITTED = TestData.NOW.minus(Duration.ofHours(2));

	private static final Instant LATER = TestData.NOW.plus(Duration.ofHours(1));

	private static final List<Answer> SKIP_PATH = List.of(new Answer("q1", "아니요"), new Answer("q4", "동아리"));

	private static final String EDITED_BODY = """
			{"answers":[{"question_id":"q1","value":"아니요"},{"question_id":"q4","value":"일반"}]}
			""";

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockOverride {

		@Bean
		@Primary
		MovableClock responseChangeClock() {
			return new MovableClock();
		}

	}

	static final class MovableClock extends Clock {

		private Instant now = TestData.NOW;

		void set(Instant instant) {
			now = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MovableClock clock;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository formVersions;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private SavedFormRepository savedForms;

	@Autowired
	private AttachmentRepository attachments;

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
		clock.set(TestData.NOW);
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		respondentId = users.saveAndFlush(TestData.newUser()).getId();
		otherId = users.saveAndFlush(TestData.newUser()).getId();
		formId = seedForm(true, true);
	}

	private UUID seedForm(boolean editable, boolean cancellable) {
		UUID id = SeedForms.load("form_required_branch.json", ownerId, forms, formVersions, responses).formId();
		Form form = forms.findById(id).orElseThrow();
		ReflectionTestUtils.setField(form, "editable", editable);
		ReflectionTestUtils.setField(form, "cancellable", cancellable);
		forms.saveAndFlush(form);
		return id;
	}

	private Response submitted(UUID form, UUID userId, int version, List<Answer> content) {
		return responses.saveAndFlush(
				new Response(form, userId, version, ResponseStatus.SUBMITTED, new ArrayList<>(content), false, null, SUBMITTED));
	}

	private Response mine() {
		return submitted(formId, respondentId, 2, SKIP_PATH);
	}

	private ResultActions edit(UUID userId, Object responseId, String body) throws Exception {
		return mockMvc.perform(patch("/me/responses/" + responseId).with(user(userId.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions cancel(UUID userId, Object responseId) throws Exception {
		return mockMvc.perform(delete("/me/responses/" + responseId).with(user(userId.toString())).with(csrf()));
	}

	private JsonNode getJson(UUID userId, String path) throws Exception {
		return JSON.readTree(mockMvc.perform(get(path).with(user(userId.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private Response reload(UUID id) {
		flushAndClear();
		return responses.findById(id).orElseThrow();
	}

	private boolean exists(UUID id) {
		flushAndClear();
		return responses.findById(id).isPresent();
	}

	private void setField(UUID form, String field, Object value) {
		Form found = forms.findById(form).orElseThrow();
		ReflectionTestUtils.setField(found, field, value);
		forms.saveAndFlush(found);
	}

	private void creatorDeleted(Response response) {
		ReflectionTestUtils.setField(response, "creatorDeletedAt", TestData.NOW);
		responses.saveAndFlush(response);
	}

	private int rows(UUID form) {
		flushAndClear();
		return jdbc.queryForObject("select count(*) from responses where form_id = ?", Integer.class, form);
	}

	private void assertUnchanged(Response original) {
		Response found = reload(original.getId());
		assertThat(found.getContent()).isEqualTo(SKIP_PATH);
		assertThat(found.getUpdatedAt()).isNull();
	}

	private JsonNode inboxItem() throws Exception {
		List<JsonNode> matches = new ArrayList<>();
		getJson(respondentId, "/me/forms").forEach(item -> {
			if (item.get("form_id").asString().equals(formId.toString())) {
				matches.add(item);
			}
		});
		assertThat(matches).hasSize(1);
		return matches.get(0);
	}

	private void inInbox(SavedFormState state, Instant savedAt) {
		savedForms.saveAndFlush(new SavedForm(respondentId, formId, state, savedAt));
	}

	private SavedFormState inboxState() {
		flushAndClear();
		return savedForms.findById(new SavedFormId(respondentId, formId)).orElseThrow().getState();
	}

	private int optionCount(String questionId, String option) throws Exception {
		JsonNode results = getJson(ownerId, "/forms/" + formId + "/results");
		for (JsonNode version : results.get("versions")) {
			if (version.get("form_version").asInt() == 2) {
				for (JsonNode question : version.get("questions")) {
					if (question.get("question_id").asString().equals(questionId)) {
						return question.get("counts").path(option).asInt(-1);
					}
				}
			}
		}
		return -1;
	}

	private int responseCount() throws Exception {
		return getJson(ownerId, "/forms/" + formId + "/results").get("response_count").asInt();
	}

	private int analyzedCount() throws Exception {
		String body = mockMvc.perform(post("/forms/" + formId + "/analyze").with(user(ownerId.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"context\":{\"conditions\":[],\"operation\":\"count\"}}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JSON.readTree(body).path("result").path("count").asInt();
	}

	private void assertConflict(ResultActions result, String code) throws Exception {
		result.andExpect(status().isConflict())
			.andExpect(content().json("{\"error\":\"" + code + "\"}", JsonCompareMode.STRICT));
	}

	// AC74

	@Test
	void response_edit_overwrites_same_id_같은_응답을_덮어쓰고_최초_제출_시각을_유지한다() throws Exception {
		Response response = mine();
		int before = rows(formId);
		clock.set(LATER);

		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk())
			.andExpect(jsonPath("$.response_id").value(response.getId().toString()))
			.andExpect(jsonPath("$.form_version").value(2))
			.andExpect(jsonPath("$.content[1].question_id").value("q4"))
			.andExpect(jsonPath("$.content[1].value").value("일반"))
			.andExpect(jsonPath("$.attachments.length()").value(0));

		Response found = reload(response.getId());
		assertThat(found.getContent()).containsExactly(new Answer("q1", "아니요"), new Answer("q4", "일반"));
		assertThat(found.getSubmittedAt()).isEqualTo(SUBMITTED);
		assertThat(found.getUpdatedAt()).isEqualTo(LATER);
		assertThat(found.getFormVersion()).isEqualTo(2);
		assertThat(found.getStatus()).isEqualTo(ResponseStatus.SUBMITTED);
		assertThat(rows(formId)).isEqualTo(before);
	}

	@Test
	void response_edit_overwrites_same_id_이력과_집계와_분석이_새_답을_쓴다() throws Exception {
		Response response = mine();
		int count = responseCount();
		assertThat(optionCount("q4", "동아리")).isEqualTo(2);
		assertThat(optionCount("q4", "일반")).isZero();

		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk());
		flushAndClear();

		assertThat(responseCount()).isEqualTo(count);
		assertThat(analyzedCount()).isEqualTo(count);
		assertThat(optionCount("q4", "동아리")).isEqualTo(1);
		assertThat(optionCount("q4", "일반")).isEqualTo(1);
		JsonNode detail = getJson(respondentId, "/me/responses/" + response.getId());
		assertThat(detail.get("content").get(1).get("value").asString()).isEqualTo("일반");
		assertThat(detail.get("updated_at").isNull()).isFalse();
	}

	@Test
	void response_edit_uses_original_version_옛_응답은_제출_버전의_규칙으로_수정한다() throws Exception {
		Map<String, Object> latest = SampleForms.parse("""
				{"sections": [{"section_id": "s1", "title": "새", "questions": [
				  {"question_id": "q9", "question_note": "새 필수 문항", "question_type": "short_answer", "options": [],
				   "required": true, "branch_rules": []}
				]}], "settings": {"display": {"default_mode": "one_question"}}}
				""");
		formVersions.saveAndFlush(new FormVersion(formId, 3, latest, TestData.NOW));
		setField(formId, "definitionVersion", 3);
		Response response = submitted(formId, respondentId, 1, SKIP_PATH);

		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk());

		Response found = reload(response.getId());
		assertThat(found.getFormVersion()).isEqualTo(1);
		assertThat(found.getContent()).contains(new Answer("q4", "일반"));
		JsonNode detail = getJson(respondentId, "/me/responses/" + response.getId());
		assertThat(detail.path("form_definition").path("definition_version").asInt()).isEqualTo(1);
	}

	@Test
	void 수정은_분기_경로를_다시_계산해_건너뛴_문항의_답을_뺀다() throws Exception {
		Response response = mine();

		edit(respondentId, response.getId(), """
				{"answers":[{"question_id":"q1","value":"아니요"},{"question_id":"q2","value":"2026-11-20"},
				 {"question_id":"q3","value":"신청"},{"question_id":"q4","value":"일반"}]}
				""").andExpect(status().isOk());

		assertThat(reload(response.getId()).getContent()).containsExactly(new Answer("q1", "아니요"), new Answer("q4", "일반"));
	}

	@Test
	void 경로가_바뀌어_필수_문항이_비면_required_missing이고_바뀌지_않는다() throws Exception {
		Response response = mine();

		edit(respondentId, response.getId(), """
				{"answers":[{"question_id":"q1","value":"예"},{"question_id":"q4","value":"일반"}]}
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("required_missing"))
			.andExpect(jsonPath("$.question_ids.length()").value(2));

		assertUnchanged(response);
	}

	@Test
	void 수정은_AI_요약_동의를_바꾸지_않고_보낸_동의_값은_무시한다() throws Exception {
		Response agreed = responses.saveAndFlush(new Response(formId, respondentId, 2, ResponseStatus.SUBMITTED,
				new ArrayList<>(SKIP_PATH), true, null, SUBMITTED));

		edit(respondentId, agreed.getId(), """
				{"answers":[{"question_id":"q1","value":"아니요"},{"question_id":"q4","value":"일반"}],
				 "ai_summary_agreed":false,"consent_agreed":false}
				""").andExpect(status().isOk());

		assertThat(reload(agreed.getId()).isAiSummaryAgreed()).isTrue();
	}

	@Test
	void 수정은_새_작성_중_응답을_만들지_않는다() throws Exception {
		Response response = mine();

		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk());

		flushAndClear();
		assertThat(responses.findDraft(respondentId, formId)).isEmpty();
	}

	@Test
	void 이_응답의_파일이_아닌_첨부_ID를_보내면_invalid_request이고_바뀌지_않는다() throws Exception {
		Response response = mine();

		edit(respondentId, response.getId(), """
				{"answers":[{"question_id":"q1","value":"아니요"},{"question_id":"q4","value":"일반"}],"attachments":["%s"]}
				""".formatted(UUID.randomUUID())).andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"invalid_request\"}", JsonCompareMode.STRICT));

		assertUnchanged(response);
	}

	@Test
	void answers가_없으면_invalid_request다() throws Exception {
		edit(respondentId, mine().getId(), "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void 첨부를_보내지_않은_수정은_기존_첨부를_유지한다() throws Exception {
		Response response = mine();
		attachments.saveAndFlush(new Attachment(formId, response.getId(), "q5", "a.pdf", "application/pdf", 1, "key", TestData.NOW));

		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk())
			.andExpect(jsonPath("$.attachments.length()").value(1))
			.andExpect(jsonPath("$.attachments[0].filename").value("a.pdf"));

		flushAndClear();
		assertThat(attachments.findByResponseIdOrderByCreatedAtAscIdAsc(response.getId())).hasSize(1);
	}

	// AC53, AC73

	@Test
	void 형식에_맞지_않는_수정은_400이고_바뀌지_않는다() throws Exception {
		Response response = submitted(formId, respondentId, 2,
				List.of(new Answer("q1", "예"), new Answer("q2", "2026-11-20"), new Answer("q3", "신청"), new Answer("q4", "일반")));

		edit(respondentId, response.getId(), """
				{"answers":[{"question_id":"q1","value":"예"},{"question_id":"q2","value":"2026-02-30"},
				 {"question_id":"q3","value":"신청"},{"question_id":"q4","value":"일반"}]}
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_input_format_value"));

		Response found = reload(response.getId());
		assertThat(found.getContent()).contains(new Answer("q2", "2026-11-20"));
		assertThat(found.getUpdatedAt()).isNull();
	}

	@Test
	void 글자_수를_넘긴_수정은_400이고_정확히_상한이면_저장한다() throws Exception {
		Map<String, Object> limited = SampleForms.parse("""
				{"sections": [{"section_id": "s1", "title": "새", "questions": [
				  {"question_id": "q1", "question_note": "소감", "question_type": "long_answer", "options": [],
				   "required": true, "character_limit": 5, "branch_rules": []}
				]}], "settings": {"display": {"default_mode": "one_question"}}}
				""");
		formVersions.saveAndFlush(new FormVersion(formId, 3, limited, TestData.NOW));
		setField(formId, "definitionVersion", 3);
		Response response = submitted(formId, respondentId, 3, List.of(new Answer("q1", "처음")));

		edit(respondentId, response.getId(), "{\"answers\":[{\"question_id\":\"q1\",\"value\":\"여섯글자입니다\"}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("character_limit_exceeded"));
		assertThat(reload(response.getId()).getContent()).containsExactly(new Answer("q1", "처음"));

		edit(respondentId, response.getId(), "{\"answers\":[{\"question_id\":\"q1\",\"value\":\"다섯글자요\"}]}")
			.andExpect(status().isOk());
	}

	// AC74 취소

	@Test
	void response_cancel_removes_one_그_응답만_결과와_이력에서_지운다() throws Exception {
		Response target = mine();
		Response other = submitted(formId, respondentId, 2, List.of(new Answer("q1", "아니요"), new Answer("q4", "일반")));
		int count = responseCount();

		cancel(respondentId, target.getId()).andExpect(status().isNoContent()).andExpect(content().string(""));

		assertThat(exists(target.getId())).isFalse();
		assertThat(reload(other.getId()).getContent()).contains(new Answer("q4", "일반"));
		assertThat(responseCount()).isEqualTo(count - 1);
		assertThat(analyzedCount()).isEqualTo(count - 1);
		assertThat(getJson(respondentId, "/me/responses")).hasSize(1);
		mockMvc.perform(get("/me/responses/" + target.getId()).with(user(respondentId.toString())))
			.andExpect(status().isNotFound());
	}

	@Test
	void 첨부가_연결된_응답을_취소하면_첨부_행도_지운다() throws Exception {
		Response response = mine();
		Attachment file = attachments.saveAndFlush(
				new Attachment(formId, response.getId(), "q5", "a.pdf", "application/pdf", 1, "key", TestData.NOW));

		cancel(respondentId, response.getId()).andExpect(status().isNoContent());

		assertThat(exists(response.getId())).isFalse();
		assertThat(attachments.findById(file.getId())).isEmpty();
	}

	// AC17

	@Test
	void partial_cancel_keeps_submitted_다른_제출이_남으면_submitted를_유지한다() throws Exception {
		inInbox(SavedFormState.SUBMITTED, SUBMITTED);
		Response first = mine();
		mine();

		cancel(respondentId, first.getId()).andExpect(status().isNoContent());

		assertThat(inboxState()).isEqualTo(SavedFormState.SUBMITTED);
	}

	@Test
	void cancel_returns_to_saved_모두_취소하면_saved이고_saved_at과_has_draft를_유지한다() throws Exception {
		inInbox(SavedFormState.SUBMITTED, SUBMITTED);
		Response only = mine();
		responses.saveAndFlush(Response.draft(formId, respondentId, 2, List.of(new Answer("q1", "예")), TestData.NOW));

		cancel(respondentId, only.getId()).andExpect(status().isNoContent());
		flushAndClear();

		JsonNode item = inboxItem();
		assertThat(item.get("state").asString()).isEqualTo("saved");
		assertThat(Instant.parse(item.get("saved_at").asString())).isEqualTo(SUBMITTED);
		assertThat(item.get("has_draft").asBoolean()).isTrue();
	}

	@Test
	void bulk_delete_keeps_submitted_history_제작자가_지운_이력만_남아도_submitted다() throws Exception {
		inInbox(SavedFormState.SUBMITTED, SUBMITTED);
		Response cancelled = mine();
		creatorDeleted(mine());

		cancel(respondentId, cancelled.getId()).andExpect(status().isNoContent());

		assertThat(inboxState()).isEqualTo(SavedFormState.SUBMITTED);
	}

	@Test
	void 내_폼함_항목이_없어도_취소된다() throws Exception {
		cancel(respondentId, mine().getId()).andExpect(status().isNoContent());
	}

	@Test
	void 다른_사용자의_제출은_내_폼함_상태에_영향을_주지_않는다() throws Exception {
		inInbox(SavedFormState.SUBMITTED, SUBMITTED);
		Response only = mine();
		submitted(formId, otherId, 2, SKIP_PATH);

		cancel(respondentId, only.getId()).andExpect(status().isNoContent());

		assertThat(inboxState()).isEqualTo(SavedFormState.SAVED);
	}

	// AC16

	@Test
	void edit_after_deadline_마감이_지나면_수정과_취소가_form_closed다() throws Exception {
		Response response = mine();
		setField(formId, "deadline", TestData.NOW.minusSeconds(60));

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "form_closed");
		assertConflict(cancel(respondentId, response.getId()), "form_closed");

		assertUnchanged(response);
	}

	@Test
	void 마감_시각_정각이면_닫혔고_직전이면_허용한다() throws Exception {
		Response response = mine();
		setField(formId, "deadline", TestData.NOW);
		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "form_closed");

		setField(formId, "deadline", TestData.NOW.plusSeconds(1));
		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk());
	}

	@Test
	void edit_closed_form_닫힌_폼은_수정과_취소가_form_closed다() throws Exception {
		Response response = mine();
		setField(formId, "status", FormStatus.CLOSED);

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "form_closed");
		assertConflict(cancel(respondentId, response.getId()), "form_closed");

		assertUnchanged(response);
	}

	@Test
	void not_editable_patch_수정만_막힌_폼은_수정_409_취소_성공이다() throws Exception {
		formId = seedForm(false, true);
		Response response = mine();

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "not_editable");
		assertUnchanged(response);

		cancel(respondentId, response.getId()).andExpect(status().isNoContent());
	}

	@Test
	void not_cancellable_delete_취소만_막힌_폼은_수정_성공_취소_409다() throws Exception {
		formId = seedForm(true, false);
		Response response = mine();

		assertConflict(cancel(respondentId, response.getId()), "not_cancellable");
		assertThat(exists(response.getId())).isTrue();

		edit(respondentId, response.getId(), EDITED_BODY).andExpect(status().isOk());
	}

	@Test
	void history_only_not_editable_제작자가_지운_응답은_수정과_취소가_history_only다() throws Exception {
		Response response = mine();
		creatorDeleted(response);

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "history_only");
		assertConflict(cancel(respondentId, response.getId()), "history_only");

		assertThat(reload(response.getId()).getContent()).isEqualTo(SKIP_PATH);
	}

	@Test
	void history_only_not_editable_폼이_삭제된_응답도_history_only다() throws Exception {
		Response response = mine();
		setField(formId, "deletedAt", TestData.NOW);

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "history_only");
		assertConflict(cancel(respondentId, response.getId()), "history_only");
	}

	// 판정 순서

	@Test
	void history_only가_form_closed보다_먼저다() throws Exception {
		Response response = mine();
		creatorDeleted(response);
		setField(formId, "status", FormStatus.CLOSED);

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "history_only");
		assertConflict(cancel(respondentId, response.getId()), "history_only");
	}

	@Test
	void form_closed가_not_editable과_not_cancellable보다_먼저다() throws Exception {
		formId = seedForm(false, false);
		Response response = mine();
		setField(formId, "status", FormStatus.CLOSED);

		assertConflict(edit(respondentId, response.getId(), EDITED_BODY), "form_closed");
		assertConflict(cancel(respondentId, response.getId()), "form_closed");
	}

	@Test
	void not_editable이_답_검증보다_먼저다() throws Exception {
		formId = seedForm(false, true);

		assertConflict(edit(respondentId, mine().getId(), "{\"answers\":[]}"), "not_editable");
	}

	@Test
	void form_closed가_답_검증보다_먼저다() throws Exception {
		Response response = mine();
		setField(formId, "status", FormStatus.CLOSED);

		assertConflict(edit(respondentId, response.getId(), "{\"answers\":[]}"), "form_closed");
	}

	// AC2

	@Test
	void 비로그인은_수정과_취소가_401이다() throws Exception {
		UUID id = mine().getId();
		mockMvc.perform(patch("/me/responses/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(EDITED_BODY))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/me/responses/" + id).with(csrf())).andExpect(status().isUnauthorized());
		assertThat(exists(id)).isTrue();
	}

	@Test
	void 다른_사용자의_응답은_수정이_404이고_바뀌지_않는다() throws Exception {
		Response response = mine();

		edit(otherId, response.getId(), EDITED_BODY).andExpect(status().isNotFound())
			.andExpect(content().json("{\"error\":\"not_found\"}", JsonCompareMode.STRICT));

		assertUnchanged(response);
	}

	@Test
	void 다른_사용자의_응답은_취소가_404이고_남아_있다() throws Exception {
		Response response = mine();

		cancel(otherId, response.getId()).andExpect(status().isNotFound());

		assertThat(exists(response.getId())).isTrue();
	}

	@Test
	void 없거나_형식이_틀린_응답은_404다() throws Exception {
		edit(respondentId, UUID.randomUUID(), EDITED_BODY).andExpect(status().isNotFound());
		edit(respondentId, "not-a-uuid", EDITED_BODY).andExpect(status().isNotFound());
		cancel(respondentId, UUID.randomUUID()).andExpect(status().isNotFound());
		cancel(respondentId, "not-a-uuid").andExpect(status().isNotFound());
	}

	@Test
	void 작성_중_응답은_수정과_취소가_404다() throws Exception {
		Response draft = responses
			.saveAndFlush(Response.draft(formId, respondentId, 2, new ArrayList<>(List.of(new Answer("q1", "예"))), TestData.NOW));

		edit(respondentId, draft.getId(), EDITED_BODY).andExpect(status().isNotFound());
		cancel(respondentId, draft.getId()).andExpect(status().isNotFound());

		assertThat(reload(draft.getId()).getStatus()).isEqualTo(ResponseStatus.IN_PROGRESS);
	}

	@Test
	void 비로그인_제출_응답은_수정과_취소가_404다() throws Exception {
		Response anonymous = submitted(formId, null, 2, SKIP_PATH);

		edit(respondentId, anonymous.getId(), EDITED_BODY).andExpect(status().isNotFound());
		cancel(respondentId, anonymous.getId()).andExpect(status().isNotFound());
	}

	// SPEC 226행: 이력의 editable·cancellable

	private void assertFlags(Response response, boolean editable, boolean cancellable) throws Exception {
		flushAndClear();
		JsonNode item = null;
		for (JsonNode candidate : getJson(respondentId, "/me/responses")) {
			if (candidate.get("response_id").asString().equals(response.getId().toString())) {
				item = candidate;
			}
		}
		assertThat(item).isNotNull();
		assertThat(item.get("editable").asBoolean()).isEqualTo(editable);
		assertThat(item.get("cancellable").asBoolean()).isEqualTo(cancellable);
		JsonNode detail = getJson(respondentId, "/me/responses/" + response.getId());
		assertThat(detail.get("editable").asBoolean()).isEqualTo(editable);
		assertThat(detail.get("cancellable").asBoolean()).isEqualTo(cancellable);
	}

	@Test
	void 이력은_열려_있고_허용되면_두_값이_true다() throws Exception {
		assertFlags(mine(), true, true);
	}

	@Test
	void 이력은_설정이_꺼진_쪽만_false다() throws Exception {
		formId = seedForm(false, true);
		assertFlags(mine(), false, true);
		formId = seedForm(true, false);
		assertFlags(mine(), true, false);
	}

	@Test
	void 이력은_닫힌_폼에서_false다() throws Exception {
		Response response = mine();
		setField(formId, "status", FormStatus.CLOSED);
		assertFlags(response, false, false);
	}

	@Test
	void 이력은_마감이_지나면_false이고_마감이_없으면_true다() throws Exception {
		Response response = mine();
		setField(formId, "deadline", TestData.NOW.minusSeconds(1));
		assertFlags(response, false, false);
		setField(formId, "deadline", null);
		assertFlags(response, true, true);
	}

	@Test
	void 이력은_제작자가_지운_응답에서_false다() throws Exception {
		Response response = mine();
		creatorDeleted(response);
		assertFlags(response, false, false);
	}

	@Test
	void 이력은_폼이_삭제되면_false다() throws Exception {
		Response response = mine();
		setField(formId, "deletedAt", TestData.NOW);
		assertFlags(response, false, false);
	}

}
