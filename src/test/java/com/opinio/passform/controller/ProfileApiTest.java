package com.opinio.passform.controller;

import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.user.Profile;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ProfileRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// /me/profiles와 POST /forms/{form_id}/autofill을 실제 Postgres로 시험한다(AC2~AC6, SPEC 5절 102~106행).
// SPEC 추적표의 골든 id는 golden_cases.yaml에 없어 일반 테스트로 하고 테스트 이름 앞에 적는다.
@Transactional
class ProfileApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String DEFINITION = """
			{
			  "title": "가상 신청", "note": "", "deadline": null,
			  "settings": {"editable": false, "cancellable": false, "response_limit": null,
			    "display": {"default_mode": "all_questions"}},
			  "payment_link": null, "consent_template_id": "consent_default", "note_attachments": [],
			  "sections": [{"section_id": "s1", "title": "기본", "questions": [
			    {"question_id": "q1", "question_note": "본인 이름", "question_type": "short_answer", "options": [],
			     "required": true, "input_format": {"kind": "name"}, "profile_key": "name"},
			    {"question_id": "q2", "question_note": "부모님 이름", "question_type": "short_answer", "options": [],
			     "required": false, "input_format": {"kind": "name"}, "profile_key": null},
			    {"question_id": "q3", "question_note": "주소", "question_type": "short_answer", "options": [],
			     "required": false, "input_format": {"kind": "address"}, "profile_key": "address"},
			    {"question_id": "q4", "question_note": "생년월일", "question_type": "short_answer", "options": [],
			     "required": false, "input_format": {"kind": "date"}, "profile_key": "birth_date"},
			    {"question_id": "q5", "question_note": "지원 동기", "question_type": "long_answer", "options": [],
			     "required": false, "profile_key": null},
			    {"question_id": "q6", "question_note": "학과", "question_type": "dropdown",
			     "options": ["소프트웨어학과", "경영학과"], "required": false, "profile_key": "department"},
			    {"question_id": "q7", "question_note": "소속 학과 다중", "question_type": "checkbox",
			     "options": ["소프트웨어학과", "경영학과"], "required": false, "profile_key": "department"},
			    {"question_id": "q8", "question_note": "이메일", "question_type": "short_answer", "options": [],
			     "required": false, "input_format": {"kind": "email"}, "profile_key": "email"}
			  ]}]
			}
			""";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private ProfileRepository profiles;

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

	private UUID me;

	private UUID other;

	private Form form;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		me = users.saveAndFlush(TestData.newUser()).getId();
		other = users.saveAndFlush(TestData.newUser()).getId();
		form = openForm(DEFINITION, 1);
	}

	private Form openForm(String definition, int version) {
		Form created = new Form(other, SampleForms.parse(definition), NOW);
		ReflectionTestUtils.setField(created, "status", com.opinio.passform.domain.form.FormStatus.OPEN);
		ReflectionTestUtils.setField(created, "definitionVersion", version);
		forms.saveAndFlush(created);
		for (int current = 1; current <= version; current++) {
			formVersions.saveAndFlush(new FormVersion(created.getId(), current, SampleForms.parse(definition), NOW));
		}
		return created;
	}

	private ResultActions create(UUID userId, String body) throws Exception {
		return mockMvc.perform(post("/me/profiles").with(user(userId.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private UUID createdId(UUID userId, String body) throws Exception {
		String reply = create(userId, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JSON.readTree(reply).path("profile_id").asString());
	}

	private ResultActions update(UUID userId, Object profileId, String body) throws Exception {
		return mockMvc.perform(put("/me/profiles/" + profileId).with(user(userId.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions remove(UUID userId, Object profileId) throws Exception {
		return mockMvc.perform(delete("/me/profiles/" + profileId).with(user(userId.toString())).with(csrf()));
	}

	private ResultActions list(UUID userId) throws Exception {
		return mockMvc.perform(get("/me/profiles").with(user(userId.toString())));
	}

	private ResultActions autofill(UUID userId, Object formId, Object profileId) throws Exception {
		return mockMvc.perform(post("/forms/" + formId + "/autofill").with(user(userId.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"profile_id\":\"" + profileId + "\"}"));
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private int rows(String table) {
		flushAndClear();
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private static String profile(String label, String personalInfo) {
		return "{\"label\":\"" + label + "\",\"personal_info\":" + personalInfo + "}";
	}

	// AC3

	@Test
	void profile_address_birth_date_allowed_허용_키를_저장하고_다시_읽는다() throws Exception {
		UUID id = createdId(me, profile("학교", """
				{"name":"김가상","student_id":"20260001","department":"소프트웨어학과","phone_number":"01000000000",
				 "email":"virtual@example.com","address":"가상시 가상로 1","birth_date":"2001-01-01"}
				"""));
		flushAndClear();

		list(me).andExpect(status().isOk()).andExpect(content().json("""
				[{"profile_id":"%s","label":"학교","personal_info":{"name":"김가상","student_id":"20260001",
				  "department":"소프트웨어학과","phone_number":"01000000000","email":"virtual@example.com",
				  "address":"가상시 가상로 1","birth_date":"2001-01-01"}}]
				""".formatted(id), JsonCompareMode.STRICT));
	}

	@Test
	void profile_rejects_sensitive_key_목록_밖_키가_있으면_아무것도_저장하지_않고_키_이름만_알린다() throws Exception {
		create(me, profile("학교", "{\"name\":\"김가상\",\"resident_number\":\"000000-0000000\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"unsupported_key\",\"keys\":[\"resident_number\"]}", JsonCompareMode.STRICT));

		assertThat(rows("profiles")).isZero();
	}

	@Test
	void profile_rejects_sensitive_key_수정도_목록_밖_키면_저장된_프로필이_그대로다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		flushAndClear();

		update(me, id, profile("바꾼 이름", "{\"name\":\"다른 값\",\"zeta\":\"a\",\"resident_number\":\"b\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"unsupported_key\",\"keys\":[\"resident_number\",\"zeta\"]}",
					JsonCompareMode.STRICT));
		flushAndClear();

		Profile saved = profiles.findById(id).orElseThrow();
		assertThat(saved.getLabel()).isEqualTo("학교");
		assertThat(saved.getPersonalInfo()).containsExactly(Map.entry("name", "김가상"));
	}

	@Test
	void 허용_키_검사가_label과_값_검사보다_먼저다() throws Exception {
		create(me, "{\"label\":\"\",\"personal_info\":{\"resident_number\":1}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("unsupported_key"));
	}

	@Test
	void 수정은_label과_값을_통째로_바꾼다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\",\"email\":\"a@example.com\"}"));

		update(me, id, profile("동아리", "{\"address\":\"가상로 2\"}")).andExpect(status().isOk()).andExpect(content().string(""));
		flushAndClear();

		Profile saved = profiles.findById(id).orElseThrow();
		assertThat(saved.getLabel()).isEqualTo("동아리");
		assertThat(saved.getPersonalInfo()).containsExactly(Map.entry("address", "가상로 2"));
	}

	@Test
	void 값의_앞뒤_공백을_지우고_빈_값은_저장하지_않으며_모두_비어도_저장한다() throws Exception {
		UUID id = createdId(me, profile("  학교  ", "{\"name\":\"  김가상 \",\"email\":\"   \"}"));
		UUID empty = createdId(me, profile("빈 프로필", "{}"));
		flushAndClear();

		Profile saved = profiles.findById(id).orElseThrow();
		assertThat(saved.getLabel()).isEqualTo("학교");
		assertThat(saved.getPersonalInfo()).containsExactly(Map.entry("name", "김가상"));
		assertThat(profiles.findById(empty).orElseThrow().getPersonalInfo()).isEmpty();
	}

	@Test
	void 프로필_형식은_저장할_때_검사하지_않는다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"email\":\"메일 아님\",\"birth_date\":\"어제\"}"));
		flushAndClear();

		assertThat(profiles.findById(id).orElseThrow().getPersonalInfo()).containsEntry("email", "메일 아님");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"label\":\"\",\"personal_info\":{}}", "{\"label\":\"   \",\"personal_info\":{}}",
			"{\"personal_info\":{}}", "{\"label\":5,\"personal_info\":{}}", "{\"label\":\"학교\"}",
			"{\"label\":\"학교\",\"personal_info\":null}", "{\"label\":\"학교\",\"personal_info\":{\"name\":1}}",
			"{\"label\":\"학교\",\"personal_info\":{\"name\":null}}", "{\"label\":\"학교\",\"personal_info\":[]}",
			"{\"label\":\"학교\",\"personal_info\":{\"name\":\"가\\u0000나\"}}",
			"{\"label\":\"학\\u0000교\",\"personal_info\":{}}" })
	void 형식이_틀린_요청은_invalid_request이고_저장하지_않는다(String body) throws Exception {
		create(me, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));

		assertThat(rows("profiles")).isZero();
	}

	@Test
	void label은_30자까지이고_같은_이름도_허용한다() throws Exception {
		String thirty = "가".repeat(30);
		createdId(me, profile(thirty, "{}"));
		createdId(me, profile(thirty, "{}"));

		create(me, profile("가".repeat(31), "{}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));
		assertThat(rows("profiles")).isEqualTo(2);
	}

	@Test
	void 오류_응답에_프로필_값이_없다() throws Exception {
		String reply = create(me, profile("학교", "{\"name\":\"비밀값\",\"resident_number\":\"비밀번호\"}")).andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(reply).doesNotContain("비밀값", "비밀번호");
	}

	// AGENTS 5절 87행

	@Test
	void 목록은_생성_시각_내림차순이고_같으면_id_오름차순이다() throws Exception {
		Instant earlier = NOW.minusSeconds(60);
		Profile old = profiles.saveAndFlush(new Profile(me, "옛것", Map.of(), earlier));
		Profile first = profiles.saveAndFlush(new Profile(me, "같은 시각1", Map.of(), NOW));
		Profile second = profiles.saveAndFlush(new Profile(me, "같은 시각2", Map.of(), NOW));
		// UUID.compareTo는 부호 있는 비교라 Postgres의 uuid 순서와 다를 수 있다. 문자열 순서가 DB 순서와 같다.
		List<String> sameTime = java.util.stream.Stream.of(first.getId().toString(), second.getId().toString())
			.sorted()
			.toList();
		flushAndClear();

		list(me).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].profile_id").value(sameTime.get(0)))
			.andExpect(jsonPath("$[1].profile_id").value(sameTime.get(1)))
			.andExpect(jsonPath("$[2].profile_id").value(old.getId().toString()));
	}

	// AC2

	@Test
	void 비로그인은_프로필과_자동채우기_모두_401이다() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(get("/me/profiles")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/me/profiles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/me/profiles/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/me/profiles/" + id).with(csrf())).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/forms/" + form.getId() + "/autofill").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"profile_id\":\"" + id + "\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 다른_사용자의_프로필은_목록에_없다() throws Exception {
		createdId(other, profile("남의 것", "{\"name\":\"남\"}"));
		UUID mine = createdId(me, profile("내 것", "{}"));
		flushAndClear();

		list(me).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].profile_id").value(mine.toString()));
	}

	@Test
	void 다른_사용자의_프로필_수정은_404이고_바뀌지_않는다() throws Exception {
		UUID theirs = createdId(other, profile("남의 것", "{\"name\":\"남\"}"));
		flushAndClear();

		update(me, theirs, profile("가로챔", "{\"name\":\"나\"}")).andExpect(status().isNotFound())
			.andExpect(content().json("{\"error\":\"not_found\"}", JsonCompareMode.STRICT));
		flushAndClear();

		Profile saved = profiles.findById(theirs).orElseThrow();
		assertThat(saved.getLabel()).isEqualTo("남의 것");
		assertThat(saved.getPersonalInfo()).containsEntry("name", "남");
	}

	@Test
	void 다른_사용자의_프로필_삭제는_404이고_남아_있다() throws Exception {
		UUID theirs = createdId(other, profile("남의 것", "{\"name\":\"남\"}"));
		flushAndClear();

		remove(me, theirs).andExpect(status().isNotFound());
		flushAndClear();

		assertThat(profiles.findById(theirs)).isPresent();
	}

	@Test
	void 다른_사용자의_프로필로_자동채우기는_404이고_값을_돌려주지_않는다() throws Exception {
		UUID theirs = createdId(other, profile("남의 것", "{\"name\":\"남의 이름\"}"));
		flushAndClear();

		String reply = autofill(me, form.getId(), theirs).andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(reply).doesNotContain("남의 이름");
	}

	@Test
	void 없거나_형식이_틀린_프로필_ID는_404다() throws Exception {
		update(me, UUID.randomUUID(), profile("a", "{}")).andExpect(status().isNotFound());
		update(me, "not-a-uuid", profile("a", "{}")).andExpect(status().isNotFound());
		remove(me, "not-a-uuid").andExpect(status().isNotFound());
		autofill(me, form.getId(), "not-a-uuid").andExpect(status().isNotFound());
	}

	// AC4

	@Test
	void autofill_selected_profile_고른_프로필의_값으로_각각_채운다() throws Exception {
		UUID school = createdId(me, profile("학교", "{\"department\":\"소프트웨어학과\",\"email\":\"school@example.com\"}"));
		UUID club = createdId(me, profile("동아리", "{\"email\":\"club@example.com\"}"));
		flushAndClear();

		autofill(me, form.getId(), school).andExpect(status().isOk()).andExpect(content().json("""
				{"answers":[{"question_id":"q6","value":"소프트웨어학과"},{"question_id":"q7","value":["소프트웨어학과"]},
				            {"question_id":"q8","value":"school@example.com"}],
				 "unfilled":["q1","q2","q3","q4","q5"]}
				""", JsonCompareMode.STRICT));
		autofill(me, form.getId(), club).andExpect(status().isOk()).andExpect(content().json("""
				{"answers":[{"question_id":"q8","value":"club@example.com"}],
				 "unfilled":["q1","q2","q3","q4","q5","q6","q7"]}
				""", JsonCompareMode.STRICT));
	}

	// AC5

	@Test
	void deleted_profile_not_used_삭제하면_값이_지워지고_자동채우기는_404이며_제출_응답은_그대로다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		Response submitted = responses.saveAndFlush(new Response(form.getId(), me, 1, ResponseStatus.SUBMITTED,
				List.of(new Answer("q1", "김가상")), false, null, NOW));
		flushAndClear();
		autofill(me, form.getId(), id).andExpect(status().isOk());

		remove(me, id).andExpect(status().isNoContent()).andExpect(content().string(""));

		assertThat(rows("profiles")).isZero();
		autofill(me, form.getId(), id).andExpect(status().isNotFound());
		assertThat(responses.findById(submitted.getId()).orElseThrow().getContent()).containsExactly(new Answer("q1", "김가상"));
	}

	// AC6

	@Test
	void autofill_own_info_only_profile_key가_있고_값이_있는_문항만_제안한다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\",\"address\":\"가상로 1\",\"birth_date\":\"2001-01-01\"}"));
		flushAndClear();

		autofill(me, form.getId(), id).andExpect(status().isOk()).andExpect(content().json("""
				{"answers":[{"question_id":"q1","value":"김가상"},{"question_id":"q3","value":"가상로 1"},
				            {"question_id":"q4","value":"2001-01-01"}],
				 "unfilled":["q2","q5","q6","q7","q8"]}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void autofill_option_mismatch_학과_값이_선택지와_다르면_unfilled다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"department\":\"소프트웨어 학과\"}"));
		flushAndClear();

		autofill(me, form.getId(), id).andExpect(status().isOk())
			.andExpect(jsonPath("$.answers.length()").value(0))
			.andExpect(jsonPath("$.unfilled.length()").value(8));
	}

	@Test
	void autofill_value_reviewable_요청_전후로_응답과_내_폼함이_바뀌지_않는다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		int responsesBefore = rows("responses");
		int savedBefore = rows("saved_forms");

		autofill(me, form.getId(), id).andExpect(status().isOk());

		assertThat(rows("responses")).isEqualTo(responsesBefore).isZero();
		assertThat(rows("saved_forms")).isEqualTo(savedBefore).isZero();
	}

	// 폼 상태

	@Test
	void 없는_폼과_형식이_틀린_폼_ID는_404다() throws Exception {
		UUID id = createdId(me, profile("학교", "{}"));

		autofill(me, UUID.randomUUID(), id).andExpect(status().isNotFound());
		autofill(me, "not-a-uuid", id).andExpect(status().isNotFound());
	}

	@Test
	void 초안_폼은_404다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		Form draft = forms.saveAndFlush(new Form(other, SampleForms.parse(DEFINITION), NOW));

		autofill(me, draft.getId(), id).andExpect(status().isNotFound());
	}

	@Test
	void 삭제된_폼은_404다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		ReflectionTestUtils.setField(form, "deletedAt", NOW);
		forms.saveAndFlush(form);

		autofill(me, form.getId(), id).andExpect(status().isNotFound());
	}

	@Test
	void 닫힌_폼은_409_form_closed다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		ReflectionTestUtils.setField(form, "status", com.opinio.passform.domain.form.FormStatus.CLOSED);
		forms.saveAndFlush(form);

		autofill(me, form.getId(), id).andExpect(status().isConflict())
			.andExpect(content().json("{\"error\":\"form_closed\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 마감이_지난_폼은_409_form_closed다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		ReflectionTestUtils.setField(form, "deadline", Instant.parse("2020-01-01T00:00:00Z"));
		forms.saveAndFlush(form);

		autofill(me, form.getId(), id).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("form_closed"));
	}

	@Test
	void 제안은_최신_배포_버전의_문항_기준이다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		String latest = """
				{"sections": [{"section_id": "s1", "title": "새", "questions": [
				  {"question_id": "q9", "question_note": "새 이름 문항", "question_type": "short_answer", "options": [],
				   "profile_key": "name"}
				]}]}
				""";
		formVersions.saveAndFlush(new FormVersion(form.getId(), 2, SampleForms.parse(latest), NOW));
		ReflectionTestUtils.setField(form, "definitionVersion", 2);
		forms.saveAndFlush(form);

		autofill(me, form.getId(), id).andExpect(status().isOk()).andExpect(content().json("""
				{"answers":[{"question_id":"q9","value":"김가상"}],"unfilled":[]}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void profile_id가_없으면_invalid_request다() throws Exception {
		mockMvc.perform(post("/forms/" + form.getId() + "/autofill").with(user(me.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void input_format이_같아도_profile_key가_없으면_채우지_않는다() throws Exception {
		UUID id = createdId(me, profile("학교", "{\"name\":\"김가상\"}"));
		flushAndClear();

		String reply = autofill(me, form.getId(), id).andReturn().getResponse().getContentAsString();
		JsonNode answers = JSON.readTree(reply).path("answers");

		assertThat(answers.size()).isEqualTo(1);
		assertThat(answers.get(0).path("question_id").asString()).isEqualTo("q1");
	}

}
