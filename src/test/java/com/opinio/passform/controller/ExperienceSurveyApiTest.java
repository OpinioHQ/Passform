package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.experience.ExperienceRating;
import com.opinio.passform.repository.ExperienceRatingRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 사용 경험 평가 설정을 켠 서버(AC95, SPEC 4절 59~61행). 실제 Postgres에 커밋하므로 트랜잭션으로 감싸지 않고,
// 만든 평가·응답·폼·사용자는 @AfterEach에서 스스로 지운다(passform_test 스키마를 다른 시험과 함께 쓴다).
// 표는 제출 트랜잭션이 끝난 뒤 만든다. 그래서 이 시험은 제출이 실제로 커밋되는 경로로 보낸다.
@TestPropertySource(properties = "passform.experience-survey.enabled=true")
class ExperienceSurveyApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String ANSWERS = """
			{"form_version":1,"answers":[{"question_id":"q1","value":"1학년"},{"question_id":"q2","value":"김가상"}],
			 "consent_agreed":true}
			""";

	private static final String NOT_FOUND = "{\"error\":\"not_found\"}";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository versions;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private ExperienceRatingRepository ratings;

	private MockMvc mockMvc;

	private final List<UUID> createdUsers = new ArrayList<>();

	private final List<UUID> createdForms = new ArrayList<>();

	private UUID ownerId;

	private UUID respondentId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		ownerId = newUser();
		respondentId = newUser();
		formId = openForm();
	}

	@AfterEach
	void cleanUp() {
		for (UUID form : createdForms) {
			jdbc.update("delete from experience_ratings where form_id = ?", form);
			jdbc.update("delete from saved_forms where form_id = ?", form);
			jdbc.update("delete from responses where form_id = ?", form);
			jdbc.update("delete from form_versions where form_id = ?", form);
			jdbc.update("delete from forms where form_id = ?", form);
		}
		for (UUID user : createdUsers) {
			jdbc.update("delete from users where user_id = ?", user);
		}
	}

	private UUID newUser() {
		UUID id = users.saveAndFlush(TestData.newUser()).getId();
		createdUsers.add(id);
		return id;
	}

	// 분기 없는 열린 폼(마감 2099년). 응답자가 취소할 수 있다.
	private UUID openForm() {
		Map<String, Object> definition = SampleForms.unbranched();
		Form form = new Form(ownerId, definition, TestData.NOW);
		ReflectionTestUtils.setField(form, "status", FormStatus.OPEN);
		ReflectionTestUtils.setField(form, "definitionVersion", 1);
		ReflectionTestUtils.setField(form, "cancellable", true);
		UUID id = forms.saveAndFlush(form).getId();
		versions.saveAndFlush(new FormVersion(id, 1, definition, TestData.NOW));
		createdForms.add(id);
		return id;
	}

	private JsonNode submit(UUID form, UUID userId) throws Exception {
		var request = post("/forms/" + form + "/responses").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(ANSWERS);
		if (userId != null) {
			request = request.with(user(userId.toString()));
		}
		return JSON.readTree(mockMvc.perform(request)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private ResultActions rate(Object form, Object responseId, String body) throws Exception {
		return mockMvc.perform(post("/forms/" + form + "/responses/" + responseId + "/experience").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String body(String token, String ease, String improvement) {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("token", token);
		if (ease != null) {
			body.put("ease", ease);
		}
		if (improvement != null) {
			body.put("improvement", improvement);
		}
		return JSON.writeValueAsString(body);
	}

	private Map<String, Object> row(String responseId) {
		return jdbc.queryForMap("select * from experience_ratings where response_id = ?", UUID.fromString(responseId));
	}

	private static String sha256(String token) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
	}

	private String ownerGet(String path) throws Exception {
		return mockMvc.perform(get(path).with(user(ownerId.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private String exportCsv() throws Exception {
		MockHttpServletResponse response = mockMvc
			.perform(post("/forms/" + formId + "/export").with(user(ownerId.toString()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"csv\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		return response.getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void 켜져_있으면_새_제출의_201에_일회용_표를_주고_DB에는_해시만_남긴다() throws Exception {
		JsonNode submitted = submit(formId, null);

		String token = submitted.get("experience_token").asString();
		String responseId = submitted.get("response_id").asString();
		assertThat(token).hasSizeGreaterThanOrEqualTo(43);
		Map<String, Object> row = row(responseId);
		assertThat(row.get("token_hash")).isEqualTo(sha256(token));
		assertThat(row.get("form_id")).isEqualTo(formId);
		assertThat(row.get("token_issued_at")).isNotNull();
		assertThat(row.get("rated_at")).isNull();
		assertThat(row.values()).doesNotContain(token);
		assertThat(submit(formId, null).get("experience_token").asString()).isNotEqualTo(token);
	}

	@Test
	void 표를_만들다_실패해도_제출은_201이고_표만_null이다() throws Exception {
		doThrow(new IllegalStateException("시험용 실패")).when(ratings).saveAndFlush(any(ExperienceRating.class));

		JsonNode submitted = submit(formId, null);

		assertThat(submitted.has("experience_token")).isTrue();
		assertThat(submitted.get("experience_token").isNull()).isTrue();
		assertThat(jdbc.queryForObject("select status from responses where response_id = ?", String.class,
				UUID.fromString(submitted.get("response_id").asString()))).isEqualTo("SUBMITTED");
		assertThat(jdbc.queryForObject("select count(*) from experience_ratings where form_id = ?", Integer.class,
				formId)).isZero();
	}

	@Test
	void 올바른_표로_한_번만_저장하고_두_번째는_409_already_rated다() throws Exception {
		JsonNode submitted = submit(formId, null);
		String token = submitted.get("experience_token").asString();
		String responseId = submitted.get("response_id").asString();

		rate(formId, responseId, body(token, "easy", "  다음 버튼이 더 컸으면 해요  ")).andExpect(status().isCreated());

		Map<String, Object> row = row(responseId);
		assertThat(row.get("ease")).isEqualTo("easy");
		assertThat(row.get("improvement")).isEqualTo("다음 버튼이 더 컸으면 해요");
		assertThat(row.get("rated_at")).isNotNull();
		rate(formId, responseId, body(token, "very_hard", null)).andExpect(status().isConflict())
			.andExpect(content().json("{\"error\":\"already_rated\"}", JsonCompareMode.STRICT));
		// 이미 평가했어도 표가 틀리면 404다. 다른 사람의 응답이 평가됐는지 드러내지 않는다.
		rate(formId, responseId, body("wrong-token", "easy", null)).andExpect(status().isNotFound())
			.andExpect(content().json(NOT_FOUND, JsonCompareMode.STRICT));
		assertThat(row(responseId).get("ease")).isEqualTo("easy");
	}

	@Test
	void 틀린_표_없는_응답_다른_폼의_응답은_구분하지_않고_404다() throws Exception {
		JsonNode submitted = submit(formId, null);
		String token = submitted.get("experience_token").asString();
		String responseId = submitted.get("response_id").asString();
		UUID otherForm = openForm();

		rate(formId, responseId, body("wrong-token", "easy", null)).andExpect(status().isNotFound())
			.andExpect(content().json(NOT_FOUND, JsonCompareMode.STRICT));
		rate(formId, UUID.randomUUID(), body(token, "easy", null)).andExpect(status().isNotFound())
			.andExpect(content().json(NOT_FOUND, JsonCompareMode.STRICT));
		rate(otherForm, responseId, body(token, "easy", null)).andExpect(status().isNotFound())
			.andExpect(content().json(NOT_FOUND, JsonCompareMode.STRICT));
		rate("not-a-uuid", "r1", body(token, "easy", null)).andExpect(status().isNotFound())
			.andExpect(content().json(NOT_FOUND, JsonCompareMode.STRICT));
		rate(formId, responseId, "{\"ease\":\"easy\"}").andExpect(status().isNotFound());
		assertThat(row(responseId).get("rated_at")).isNull();
	}

	@Test
	void 두_문항이_모두_비거나_범위_밖이거나_1000자를_넘으면_400_invalid_experience다() throws Exception {
		JsonNode submitted = submit(formId, null);
		String token = submitted.get("experience_token").asString();
		String responseId = submitted.get("response_id").asString();
		String invalid = "{\"error\":\"invalid_experience\"}";

		rate(formId, responseId, body(token, null, null)).andExpect(status().isBadRequest())
			.andExpect(content().json(invalid, JsonCompareMode.STRICT));
		rate(formId, responseId, body(token, null, "   ")).andExpect(status().isBadRequest())
			.andExpect(content().json(invalid, JsonCompareMode.STRICT));
		// 4단계로 바꾸면서 없앤 "보통"
		rate(formId, responseId, body(token, "neutral", null)).andExpect(status().isBadRequest())
			.andExpect(content().json(invalid, JsonCompareMode.STRICT));
		rate(formId, responseId, body(token, null, "가".repeat(1001))).andExpect(status().isBadRequest())
			.andExpect(content().json(invalid, JsonCompareMode.STRICT));
		assertThat(row(responseId).get("rated_at")).isNull();

		// 경계: 개선 의견만 1000자는 저장된다.
		rate(formId, responseId, body(token, null, "가".repeat(1000))).andExpect(status().isCreated());
		assertThat(row(responseId).get("ease")).isNull();
		assertThat((String) row(responseId).get("improvement")).hasSize(1000);
	}

	@Test
	void 응답자가_제출을_취소하면_평가는_남고_응답과의_연결만_끊긴다() throws Exception {
		JsonNode submitted = submit(formId, respondentId);
		String token = submitted.get("experience_token").asString();
		String responseId = submitted.get("response_id").asString();
		rate(formId, responseId, body(token, "very_easy", null)).andExpect(status().isCreated());
		UUID ratingId = (UUID) row(responseId).get("experience_rating_id");

		mockMvc.perform(delete("/me/responses/" + responseId).with(user(respondentId.toString())).with(csrf()))
			.andExpect(status().isNoContent());

		Map<String, Object> left = jdbc.queryForMap("select * from experience_ratings where experience_rating_id = ?",
				ratingId);
		assertThat(left.get("response_id")).isNull();
		assertThat(left.get("ease")).isEqualTo("very_easy");
		assertThat(jdbc.queryForObject("select count(*) from responses where response_id = ?", Integer.class,
				UUID.fromString(responseId))).isZero();
	}

	@Test
	void 제작자가_받은_응답을_모두_지우면_비로그인_응답의_평가는_연결만_끊긴다() throws Exception {
		JsonNode submitted = submit(formId, null);
		String responseId = submitted.get("response_id").asString();
		rate(formId, responseId, body(submitted.get("experience_token").asString(), "hard", "글씨가 작아요"))
			.andExpect(status().isCreated());
		UUID ratingId = (UUID) row(responseId).get("experience_rating_id");

		mockMvc.perform(delete("/forms/" + formId + "/responses").with(user(ownerId.toString())).with(csrf()))
			.andExpect(status().is2xxSuccessful());

		Map<String, Object> left = jdbc.queryForMap("select * from experience_ratings where experience_rating_id = ?",
				ratingId);
		assertThat(left.get("response_id")).isNull();
		assertThat(left.get("form_id")).isEqualTo(formId);
		assertThat(left.get("improvement")).isEqualTo("글씨가 작아요");
	}

	@Test
	void 평가는_제작자의_결과_통계_내보내기_저장_공간에_들어가지_않는다() throws Exception {
		JsonNode first = submit(formId, null);
		submit(formId, respondentId);
		String results = ownerGet("/forms/" + formId + "/results");
		String list = ownerGet("/forms/" + formId + "/responses");
		String csv = exportCsv();
		String storage = ownerGet("/me/storage");
		assertThat(JSON.readTree(results).get("response_count").asInt()).isEqualTo(2);

		rate(formId, first.get("response_id").asString(),
				body(first.get("experience_token").asString(), "easy", "다음 버튼이 더 컸으면 해요"))
			.andExpect(status().isCreated());

		assertThat(ownerGet("/forms/" + formId + "/results")).isEqualTo(results);
		assertThat(ownerGet("/forms/" + formId + "/responses")).isEqualTo(list);
		assertThat(exportCsv()).isEqualTo(csv).doesNotContain("다음 버튼이 더 컸으면 해요");
		assertThat(ownerGet("/me/storage")).isEqualTo(storage);
	}

	// 기존 시험(AttachmentApiTest 등)은 delete from responses·forms를 직접 실행한다. 평가 행이 있어도 막히지 않아야 한다.
	@Test
	void 평가_행이_있어도_응답과_폼을_SQL로_바로_지울_수_있다() throws Exception {
		JsonNode submitted = submit(formId, null);
		String responseId = submitted.get("response_id").asString();
		rate(formId, responseId, body(submitted.get("experience_token").asString(), "easy", null))
			.andExpect(status().isCreated());
		UUID ratingId = (UUID) row(responseId).get("experience_rating_id");

		jdbc.update("delete from responses where form_id = ?", formId);
		jdbc.update("delete from form_versions where form_id = ?", formId);
		jdbc.update("delete from forms where form_id = ?", formId);

		assertThat(jdbc.queryForObject("select response_id from experience_ratings where experience_rating_id = ?",
				UUID.class, ratingId)).isNull();
	}

}
