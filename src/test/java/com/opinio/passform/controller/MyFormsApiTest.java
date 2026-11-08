package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 내 폼함과 제출 이력(SPEC 5절 192~193행, 199~200행, AC2, AC15, AC17)을 실제 Postgres와 form_required_branch로 시험한다.
// fixture: 버전 1·2(q1 문구가 다르다), 최신 2, 마감 2026-11-30. 시계는 테스트가 옮기는 고정 시계다.
@Transactional
class MyFormsApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Instant T1 = TestData.NOW.minus(Duration.ofHours(2));

	private static final Instant T2 = TestData.NOW.minus(Duration.ofHours(1));

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockOverride {

		@Bean
		@Primary
		MovableClock movableClock() {
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

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID respondentId;

	private UUID otherId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		clock.set(TestData.NOW);
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		respondentId = users.saveAndFlush(TestData.newUser()).getId();
		otherId = users.saveAndFlush(TestData.newUser()).getId();
	}

	private UUID seedForm() {
		return SeedForms.load("form_required_branch.json", ownerId, forms, formVersions, responses).formId();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object value) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(value));
	}

	private ResultActions save(UUID userId, Object formId) throws Exception {
		return mockMvc.perform(json(post("/me/saved-forms"), Map.of("form_id", String.valueOf(formId)))
			.with(user(userId.toString())));
	}

	private ResultActions submit(UUID userId, UUID formId, String draftId) throws Exception {
		Map<String, Object> body = new java.util.HashMap<>(Map.of("form_version", 2, "answers",
				List.of(Map.of("question_id", "q1", "value", "아니요"), Map.of("question_id", "q4", "value", "동아리")),
				"consent_agreed", true));
		if (draftId != null) {
			body.put("draft_response_id", draftId);
		}
		return mockMvc.perform(json(post("/forms/" + formId + "/responses"), body).with(user(userId.toString())));
	}

	private JsonNode getJson(UUID userId, String path) throws Exception {
		String body = mockMvc.perform(get(path).with(user(userId.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JSON.readTree(body);
	}

	private JsonNode inboxItem(UUID userId, UUID formId) throws Exception {
		List<JsonNode> matches = new ArrayList<>();
		getJson(userId, "/me/forms").forEach(item -> {
			if (item.get("form_id").asString().equals(formId.toString())) {
				matches.add(item);
			}
		});
		assertThat(matches).as("내 폼함 항목은 폼당 하나").hasSize(1);
		return matches.get(0);
	}

	private static List<String> ids(JsonNode list, String field) {
		List<String> result = new ArrayList<>();
		list.forEach(item -> result.add(item.get(field).asString()));
		return result;
	}

	private void setDeadline(UUID formId, Instant deadline) {
		Form form = forms.findById(formId).orElseThrow();
		ReflectionTestUtils.setField(form, "deadline", deadline);
		forms.saveAndFlush(form);
	}

	@Test
	void 저장은_처음_201_다시_200이고_saved_at을_바꾸지_않는다() throws Exception {
		UUID formId = seedForm();
		clock.set(T1);
		save(respondentId, formId).andExpect(status().isCreated());
		clock.set(T2);
		save(respondentId, formId).andExpect(status().isOk());

		JsonNode item = inboxItem(respondentId, formId);
		assertThat(item.get("state").asString()).isEqualTo("saved");
		assertThat(Instant.parse(item.get("saved_at").asString())).isEqualTo(T1);
		assertThat(item.get("submitted_at").isNull()).isTrue();
	}

	@Test
	void 남의_초안과_없는_폼은_404다() throws Exception {
		Form draft = forms.saveAndFlush(new Form(ownerId, Map.of("title", "초안"), TestData.NOW));
		save(respondentId, draft.getId()).andExpect(status().isNotFound());
		save(respondentId, UUID.randomUUID()).andExpect(status().isNotFound());
		save(respondentId, "form_a").andExpect(status().isNotFound());
		assertThat(getJson(respondentId, "/me/forms")).isEmpty();
	}

	// AC17: 저장하지 않았던 폼, 이미 저장한 폼, 초안 제출 모두 제출 뒤 submitted이고 항목은 폼당 하나다.
	@Test
	void 제출하면_내_폼함_항목이_submitted가_된다() throws Exception {
		UUID unsaved = seedForm();
		UUID saved = seedForm();
		UUID drafted = seedForm();
		clock.set(T1);
		save(respondentId, saved).andExpect(status().isCreated());
		mockMvc.perform(json(put("/me/forms/" + drafted + "/response-draft"),
				Map.of("form_version", 2, "answers", List.of(Map.of("question_id", "q1", "value", "아니요"))))
			.with(user(respondentId.toString()))).andExpect(status().isCreated());
		String draftId = responses.findDraft(respondentId, drafted).orElseThrow().getId().toString();

		clock.set(T2);
		submit(respondentId, unsaved, null).andExpect(status().isCreated());
		submit(respondentId, saved, null).andExpect(status().isCreated());
		submit(respondentId, drafted, draftId).andExpect(status().isCreated());

		JsonNode first = inboxItem(respondentId, unsaved);
		assertThat(first.get("state").asString()).isEqualTo("submitted");
		assertThat(Instant.parse(first.get("saved_at").asString())).isEqualTo(T2);
		assertThat(Instant.parse(first.get("submitted_at").asString())).isEqualTo(T2);
		assertThat(first.get("has_draft").asBoolean()).isFalse();

		JsonNode second = inboxItem(respondentId, saved);
		assertThat(second.get("state").asString()).isEqualTo("submitted");
		assertThat(Instant.parse(second.get("saved_at").asString())).isEqualTo(T1);
		assertThat(Instant.parse(second.get("submitted_at").asString())).isEqualTo(T2);

		JsonNode third = inboxItem(respondentId, drafted);
		assertThat(third.get("state").asString()).isEqualTo("submitted");
		assertThat(Instant.parse(third.get("saved_at").asString())).isEqualTo(T1);
		assertThat(third.get("has_draft").asBoolean()).isFalse();

		// 남의 내 폼함에는 들어가지 않는다.
		assertThat(getJson(otherId, "/me/forms")).isEmpty();
	}

	@Test
	void 내_폼함은_최근_저장순이고_has_draft와_closed를_계산한다() throws Exception {
		UUID older = seedForm();
		UUID drafted = seedForm();
		UUID closed = seedForm();
		clock.set(T1);
		save(respondentId, older).andExpect(status().isCreated());
		clock.set(T2);
		save(respondentId, drafted).andExpect(status().isCreated());
		save(respondentId, closed).andExpect(status().isCreated());
		mockMvc.perform(json(put("/me/forms/" + drafted + "/response-draft"),
				Map.of("form_version", 2, "answers", List.of()))
			.with(user(respondentId.toString()))).andExpect(status().isCreated());
		clock.set(TestData.NOW);
		setDeadline(closed, TestData.NOW.minusSeconds(1));
		setDeadline(drafted, TestData.NOW.plusSeconds(1));

		JsonNode list = getJson(respondentId, "/me/forms");
		List<String> sameTime = new ArrayList<>(List.of(drafted.toString(), closed.toString()));
		sameTime.sort(Comparator.naturalOrder());
		assertThat(ids(list, "form_id")).containsExactly(sameTime.get(0), sameTime.get(1), older.toString());
		assertThat(inboxItem(respondentId, drafted).get("has_draft").asBoolean()).isTrue();
		assertThat(inboxItem(respondentId, drafted).get("closed").asBoolean()).isFalse();
		assertThat(inboxItem(respondentId, older).get("has_draft").asBoolean()).isFalse();
		assertThat(inboxItem(respondentId, closed).get("closed").asBoolean()).isTrue();
		assertThat(inboxItem(respondentId, older).get("title").asString()).isEqualTo("행사 참석 신청");
	}

	@Test
	void 제출_이력은_본인의_제출만_최근순이고_같으면_response_id_순이다() throws Exception {
		UUID formId = seedForm();
		UUID otherForm = seedForm();
		String older = responses.saveAndFlush(TestData.submitted(formId, respondentId, 2, T1)).getId().toString();
		String tieA = responses.saveAndFlush(TestData.submitted(formId, respondentId, 2, T2)).getId().toString();
		String tieB = responses.saveAndFlush(TestData.submitted(otherForm, respondentId, 1, T2)).getId().toString();
		responses.saveAndFlush(TestData.withStatus(formId, respondentId, ResponseStatus.IN_PROGRESS));
		responses.saveAndFlush(TestData.submitted(formId, otherId, 2, TestData.NOW));

		JsonNode list = getJson(respondentId, "/me/responses");
		List<String> ties = new ArrayList<>(List.of(tieA, tieB));
		ties.sort(Comparator.naturalOrder());
		assertThat(ids(list, "response_id")).containsExactly(ties.get(0), ties.get(1), older);
		JsonNode first = list.get(2);
		assertThat(first.get("form_id").asString()).isEqualTo(formId.toString());
		assertThat(first.get("form_title").asString()).isEqualTo("행사 참석 신청");
		assertThat(first.get("form_version").asInt()).isEqualTo(2);
		assertThat(first.get("editable").asBoolean()).isFalse();
		assertThat(first.get("cancellable").asBoolean()).isFalse();
	}

	// AC15: 제출 뒤 새 버전이 배포돼도 상세는 제출 버전의 문구다. fixture의 버전 1·2는 q1 문구가 다르다.
	@Test
	void 상세는_제출_버전의_문항_문구로_답을_돌려준다() throws Exception {
		UUID formId = seedForm();
		String responseId = responses.saveAndFlush(TestData.submitted(formId, respondentId, 1, T1)).getId().toString();

		mockMvc.perform(get("/me/responses/" + responseId).with(user(respondentId.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.form_version").value(1))
			.andExpect(jsonPath("$.form_definition.definition_version").value(1))
			.andExpect(jsonPath("$.content[0].question_id").value("q1"))
			.andExpect(jsonPath("$.content[0].question_note").value("행사에 참석하나요?"))
			.andExpect(jsonPath("$.content[0].value").value("예"))
			.andExpect(jsonPath("$.attachments").isEmpty())
			.andExpect(jsonPath("$.submitted_at").value(T1.toString()));
	}

	// AC2: 남의 응답·작성 중 응답·없는 응답은 같은 404이고 본문에 답이 없다.
	@Test
	void 남의_응답과_작성_중_응답은_404이고_본문에_원문이_없다() throws Exception {
		UUID formId = seedForm();
		Response others = responses.saveAndFlush(new Response(formId, otherId, 2, ResponseStatus.SUBMITTED,
				List.of(new Answer("q5", "남에게 보이면 안 되는 답")), false, null, T1));
		Response draft = responses.saveAndFlush(TestData.withStatus(formId, respondentId, ResponseStatus.IN_PROGRESS));

		for (String id : List.of(others.getId().toString(), draft.getId().toString(), UUID.randomUUID().toString(),
				"rb01")) {
			String body = mockMvc.perform(get("/me/responses/" + id).with(user(respondentId.toString())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(body).doesNotContain("남에게 보이면 안 되는 답");
		}
		assertThat(getJson(respondentId, "/me/responses")).isEmpty();
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		UUID formId = seedForm();
		mockMvc.perform(get("/me/forms")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me/responses")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me/responses/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
		mockMvc.perform(json(post("/me/saved-forms"), Map.of("form_id", formId.toString())))
			.andExpect(status().isUnauthorized());
	}

}
