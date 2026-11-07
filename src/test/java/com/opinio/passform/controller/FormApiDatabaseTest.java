package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.domain.template.ConsentNotices;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

// SPEC 5절 115~120행, 187행의 입출력과 오류 코드를 실제 Postgres까지 포함한 전체 경로로 시험한다.
class FormApiDatabaseTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${passform.frontend-origin}")
	private String frontendOrigin;

	private MockMvc mockMvc;

	private UUID owner;

	private UUID other;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		owner = users.saveAndFlush(TestData.newUser()).getId();
		other = users.saveAndFlush(TestData.newUser()).getId();
	}

	// 전체 경로를 시험하느라 커밋하므로 직접 지운다. 같은 스키마를 쓰는 다른 DB 테스트가 전체 행 수를 본다.
	@AfterEach
	void removeCommittedRows() {
		Object[] userIds = { owner, other };
		String ownedForms = "(select form_id from forms where owner_id in (?, ?))";
		jdbc.update("delete from responses where form_id in " + ownedForms, userIds);
		jdbc.update("delete from form_versions where form_id in " + ownedForms, userIds);
		jdbc.update("delete from forms where owner_id in (?, ?)", userIds);
		jdbc.update("delete from users where user_id in (?, ?)", userIds);
	}

	private ResultActions postForm(UUID user, Object body) throws Exception {
		return mockMvc.perform(post("/forms").with(user(user.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)));
	}

	private ResultActions patchForm(UUID user, String formId, Object body) throws Exception {
		return mockMvc.perform(patch("/forms/" + formId).with(user(user.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)));
	}

	private ResultActions readDefinition(UUID user, String formId) throws Exception {
		return mockMvc.perform(get("/forms/" + formId + "/definition").with(user(user.toString())));
	}

	private ResultActions publicById(String formId) throws Exception {
		return mockMvc.perform(get("/forms/" + formId));
	}

	private String createDraft(Map<String, Object> definition) throws Exception {
		String body = postForm(owner, definition).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JSON.readTree(body).get("form_id").asString();
	}

	private String createOpen(Map<String, Object> definition) throws Exception {
		String formId = createDraft(definition);
		patchForm(owner, formId, Map.of("status", "open")).andExpect(status().isOk());
		return formId;
	}

	private void expectError(ResultActions result, int status, String error) throws Exception {
		result.andExpect(status().is(status))
			.andExpect(MockMvcResultMatchers.content()
				.json("{\"error\":\"" + error + "\"}", JsonCompareMode.STRICT));
	}

	private JsonNode definitionOf(String formId) throws Exception {
		return JSON.readTree(readDefinition(owner, formId).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}

	private int versionCount(String formId) {
		return jdbc.queryForObject("select count(*) from form_versions where form_id = ?::uuid", Integer.class, formId);
	}

	private String versionTitle(String formId, int version) {
		return jdbc.queryForObject("select definition ->> 'title' from form_versions where form_id = ?::uuid and version = ?",
				String.class, formId, version);
	}

	private void addSubmitted(String formId, int count) {
		for (int i = 0; i < count; i++) {
			responses.saveAndFlush(TestData.submitted(UUID.fromString(formId), null, 1, TestData.NOW));
		}
	}

	@Test
	void 초안부터_버전_2까지_전체_흐름() throws Exception {
		// 초안 저장: 미완성(동의 템플릿 없음, 없는 대상으로 분기)도 저장되고 버전은 없다.
		Map<String, Object> draft = SampleForms.branched();
		draft.put("consent_template_id", null);
		SampleForms.question(draft, 0, 1).put("branch_rules",
				List.of(SampleForms.parse("""
						{"when": {"match": "all", "conditions": [{"question_id": "q2", "option": "예"}]},
						 "go_to": {"section_id": "없는_섹션"}}
						""")));
		String formId = createDraft(draft);
		readDefinition(owner, formId).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.definition_version").value(nullValue()))
			.andExpect(jsonPath("$.share_url").value(nullValue()));
		JsonNode savedDraft = definitionOf(formId);

		// 배포 실패 1: 분기 대상이 없다. 초안·버전 불변.
		expectError(patchForm(owner, formId, Map.of("status", "open")), 400, "invalid_branch");
		assertThat(definitionOf(formId)).isEqualTo(savedDraft);
		assertThat(versionCount(formId)).isZero();

		// 배포 실패 2: 함께 보낸 수정도 저장되지 않는다.
		Map<String, Object> fixBranch = new LinkedHashMap<>(SampleForms.branched());
		fixBranch.put("consent_template_id", null);
		fixBranch.put("status", "open");
		expectError(patchForm(owner, formId, fixBranch), 400, "consent_template_required");
		assertThat(definitionOf(formId)).isEqualTo(savedDraft);
		assertThat(versionCount(formId)).isZero();
		publicById(formId).andExpect(status().isNotFound());

		// 배포 성공: 버전 1과 share_url.
		Map<String, Object> publish = new LinkedHashMap<>(SampleForms.branched());
		publish.put("status", "open");
		String stateBody = patchForm(owner, formId, publish).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("open"))
			.andExpect(jsonPath("$.definition_version").value(1))
			.andExpect(jsonPath("$.share_url").value(matchesPattern(java.util.regex.Pattern.quote(frontendOrigin)
					+ "/f/[A-Za-z0-9_-]{22}")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String shareUrl = JSON.readTree(stateBody).get("share_url").asString();
		String slugPath = shareUrl.substring(frontendOrigin.length());

		// 공개 조회: slug와 ID가 같은 폼을 준다.
		mockMvc.perform(get(slugPath))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.form_id").value(formId))
			.andExpect(jsonPath("$.definition_version").value(1))
			.andExpect(jsonPath("$.title").value("가상 분기"))
			.andExpect(jsonPath("$.sections", hasSize(3)))
			.andExpect(jsonPath("$.display.default_mode").value("section_page"))
			.andExpect(jsonPath("$.display.allowed_modes", containsInRelativeOrder("section_page")))
			.andExpect(jsonPath("$.display.allowed_modes", hasSize(1)));
		publicById(formId).andExpect(status().isOk()).andExpect(jsonPath("$.form_id").value(formId));

		// 정의 수정: 버전 2, 버전 1 그대로.
		patchForm(owner, formId, Map.of("title", "고친 제목")).andExpect(status().isOk())
			.andExpect(jsonPath("$.definition_version").value(2))
			.andExpect(jsonPath("$.share_url").value(shareUrl));
		assertThat(versionTitle(formId, 1)).isEqualTo("가상 분기");
		assertThat(versionTitle(formId, 2)).isEqualTo("고친 제목");
		publicById(formId).andExpect(jsonPath("$.definition_version").value(2))
			.andExpect(jsonPath("$.title").value("고친 제목"));

		// 운영 설정만 수정: 버전 그대로이고 현재 값은 바로 적용된다.
		Map<String, Object> settings = SampleForms.settings(SampleForms.branched());
		settings.put("editable", true);
		settings.put("response_limit", 5);
		Map<String, Object> operating = new LinkedHashMap<>();
		operating.put("settings", settings);
		operating.put("deadline", "2099-01-01T09:00:00+09:00");
		patchForm(owner, formId, operating).andExpect(status().isOk())
			.andExpect(jsonPath("$.definition_version").value(2));
		assertThat(versionCount(formId)).isEqualTo(2);
		publicById(formId).andExpect(jsonPath("$.deadline").value("2099-01-01T00:00:00Z"));
		assertThat(jdbc.queryForMap("select editable, response_limit from forms where form_id = ?::uuid", formId))
			.containsEntry("editable", true)
			.containsEntry("response_limit", 5);
	}

	@Test
	void POST는_draft를_만들고_owner를_요청자로_둔다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		assertThat(jdbc.queryForObject("select owner_id from forms where form_id = ?::uuid", UUID.class, formId))
			.isEqualTo(owner);
		postForm(owner, SampleForms.unbranched()).andExpect(jsonPath("$.status").value("draft"));
	}

	@Test
	void GET_definition은_정의_전체와_상태를_한_객체로_준다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		JsonNode body = definitionOf(formId);

		Map<String, Object> expected = SampleForms.unbranched();
		expected.put("deadline", "2099-11-30T14:59:00Z");
		expected.put("status", "draft");
		expected.put("share_url", null);
		expected.put("definition_version", null);
		assertThat(body).isEqualTo(JSON.valueToTree(expected));
	}

	@Test
	void AC42_형식과_본인_정보_설정은_저장_뒤에도_같다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		JsonNode question = definitionOf(formId).get("sections").get(0).get("questions").get(1);

		assertThat(question.get("input_format").get("kind").asString()).isEqualTo("name");
		assertThat(question.get("profile_key").asString()).isEqualTo("name");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"title\": \"제목만\"}", "[]", "\"text\"", "1" })
	void POST에_폼_정의가_아닌_JSON은_schema_invalid다(String body) throws Exception {
		expectError(mockMvc.perform(post("/forms").with(user(owner.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)), 400, "schema_invalid");
	}

	@Test
	void SPEC_437행_default_mode가_없으면_schema_invalid다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.settings(definition).put("display", Map.of());

		expectError(postForm(owner, definition), 400, "schema_invalid");
	}

	@Test
	void POST에_status를_보내면_schema_invalid다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("status", "open");

		expectError(postForm(owner, definition), 400, "schema_invalid");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-30", "2026-11-30T23:59:00", "내일" })
	void 타임존_없는_deadline은_schema_invalid다(String deadline) throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("deadline", deadline);

		expectError(postForm(owner, definition), 400, "schema_invalid");
	}

	@Test
	void F3_틀린_구조는_초안이어도_저장하지_않는다() throws Exception {
		Map<String, Object> shortWithOptions = SampleForms.unbranched();
		SampleForms.question(shortWithOptions, 0, 1).put("options", List.of("선택지"));
		expectError(postForm(owner, shortWithOptions), 400, "schema_invalid");

		Map<String, Object> duplicateOptions = SampleForms.unbranched();
		SampleForms.question(duplicateOptions, 0, 0).put("options", List.of("같음", "같음"));
		expectError(postForm(owner, duplicateOptions), 400, "schema_invalid");

		Map<String, Object> formatOnChoice = SampleForms.unbranched();
		SampleForms.question(formatOnChoice, 0, 0).put("input_format", Map.of("kind", "name"));
		expectError(postForm(owner, formatOnChoice), 400, "schema_invalid");

		Map<String, Object> limitOnChoice = SampleForms.unbranched();
		SampleForms.question(limitOnChoice, 0, 0).put("character_limit", 10);
		expectError(postForm(owner, limitOnChoice), 400, "schema_invalid");
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://pay.example.com", "pay.example.com", "javascript:alert(1)", "https://" })
	void AC11_https가_아닌_링크는_invalid_payment_link다(String link) throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("payment_link", link);
		expectError(postForm(owner, definition), 400, "invalid_payment_link");

		String formId = createDraft(SampleForms.unbranched());
		expectError(patchForm(owner, formId, Map.of("payment_link", link)), 400, "invalid_payment_link");
	}

	@Test
	void AC11_https_링크는_그대로_저장하고_보여_준다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("payment_link", "https://pay.example.com/가상");
		String formId = createOpen(definition);

		publicById(formId).andExpect(jsonPath("$.payment_link").value("https://pay.example.com/가상"));
	}

	@Test
	void 링크_타입이_틀리면_schema_invalid다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("payment_link", 1);

		expectError(postForm(owner, definition), 400, "schema_invalid");
	}

	@Test
	void PATCH는_보낸_키만_통째로_바꾼다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		patchForm(owner, formId, Map.of("sections", List.of())).andExpect(status().isOk());

		JsonNode saved = definitionOf(formId);
		assertThat(saved.get("title").asString()).isEqualTo("가상 신청");
		assertThat(saved.get("sections")).isEmpty();
	}

	@Test
	void PATCH에_모르는_키나_틀린_값은_schema_invalid이고_바꾸지_않는다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());
		JsonNode before = definitionOf(formId);

		expectError(patchForm(owner, formId, Map.of("unknown", 1)), 400, "schema_invalid");
		expectError(patchForm(owner, formId, Map.of("title", 1)), 400, "schema_invalid");
		expectError(patchForm(owner, formId, Map.of("status", "published")), 400, "schema_invalid");
		expectError(patchForm(owner, formId, List.of()), 400, "schema_invalid");

		assertThat(definitionOf(formId)).isEqualTo(before);
	}

	@Test
	void AC56_draft_저장은_의미_검증을_하지_않고_버전을_올리지_않는다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		Map<String, Object> invalid = new LinkedHashMap<>();
		invalid.put("consent_template_id", null);
		invalid.put("title", "");
		invalid.put("sections", SampleForms.branched().get("sections"));
		patchForm(owner, formId, invalid).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.definition_version").value(nullValue()));
		assertThat(versionCount(formId)).isZero();
	}

	@Test
	void AC46_배포할_때_보기_방식을_검사한다() throws Exception {
		Map<String, Object> definition = SampleForms.branched();
		SampleForms.settings(definition).put("display", Map.of("default_mode", "all_questions"));
		String formId = createDraft(definition);

		expectError(patchForm(owner, formId, Map.of("status", "open")), 400, "invalid_display_mode");
		assertThat(versionCount(formId)).isZero();
	}

	@Test
	void F4_문구가_빈_문항은_배포되지_않지만_초안에는_저장된다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.question(definition, 0, 0).put("question_note", "");
		String formId = createDraft(definition);

		expectError(patchForm(owner, formId, Map.of("status", "open")), 400, "schema_invalid");
		readDefinition(owner, formId).andExpect(jsonPath("$.status").value("draft"));
	}

	@Test
	void F4_문항이_하나도_없는_폼은_초안에_저장되지만_배포되지_않는다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("sections", List.of());
		String formId = createDraft(definition);
		JsonNode before = definitionOf(formId);

		expectError(patchForm(owner, formId, Map.of("status", "open")), 400, "schema_invalid");

		assertThat(definitionOf(formId)).isEqualTo(before);
		readDefinition(owner, formId).andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.definition_version").value(nullValue()))
			.andExpect(jsonPath("$.share_url").value(nullValue()));
		assertThat(versionCount(formId)).isZero();
		expectError(publicById(formId), 404, "not_found");
	}

	@Test
	void F4_배포된_폼의_문항을_모두_지우는_저장은_거부하고_마지막_배포본은_그대로다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		JsonNode before = definitionOf(formId);

		expectError(patchForm(owner, formId, Map.of("sections", List.of())), 400, "schema_invalid");

		assertThat(definitionOf(formId)).isEqualTo(before);
		assertThat(versionCount(formId)).isEqualTo(1);
		publicById(formId).andExpect(status().isOk()).andExpect(jsonPath("$.definition_version").value(1));
	}

	@Test
	@SuppressWarnings("unchecked")
	void F4_문항이_있는_폼에_빈_섹션이_섞여_있으면_배포된다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		((List<Object>) definition.get("sections"))
			.add(Map.of("section_id", "s3", "title", "빈 섹션", "questions", List.of()));

		String formId = createOpen(definition);

		publicById(formId).andExpect(status().isOk()).andExpect(jsonPath("$.sections", hasSize(3)));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "NULL", value = { "NULL", "없는_템플릿" })
	void AC12_동의_템플릿이_없으면_배포되지_않는다(String templateId) throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		definition.put("consent_template_id", templateId);
		String formId = createDraft(definition);

		expectError(patchForm(owner, formId, Map.of("status", "open")), 400, "consent_template_required");
		readDefinition(owner, formId).andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.definition_version").value(nullValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "open", "closed" })
	void AC12_AC8_배포된_폼의_저장도_의미_검증을_한다(String state) throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		if ("closed".equals(state)) {
			patchForm(owner, formId, Map.of("status", "closed")).andExpect(status().isOk());
		}
		JsonNode before = definitionOf(formId);

		Map<String, Object> noConsent = new LinkedHashMap<>();
		noConsent.put("consent_template_id", null);
		expectError(patchForm(owner, formId, noConsent), 400, "consent_template_required");
		Map<String, Object> badBranch = SampleForms.branched();
		SampleForms.question(badBranch, 0, 1).put("branch_rules", List.of(SampleForms.parse("""
				{"when": {"match": "all", "conditions": [{"question_id": "q2", "option": "없는 선택지"}]},
				 "go_to": {"submit": true}}
				""")));
		expectError(patchForm(owner, formId, Map.of("sections", badBranch.get("sections"))), 400, "invalid_branch");
		Map<String, Object> sectionPage = SampleForms.settings(SampleForms.unbranched());
		sectionPage.put("display", Map.of("default_mode", "section_page"));
		expectError(patchForm(owner, formId, Map.of("settings", sectionPage)), 400, "invalid_display_mode");

		assertThat(definitionOf(formId)).isEqualTo(before);
		assertThat(versionCount(formId)).isEqualTo(1);
	}

	@Test
	void AC58_closed_폼의_정의를_고치면_다음_버전이다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		patchForm(owner, formId, Map.of("status", "closed")).andExpect(status().isOk());

		patchForm(owner, formId, Map.of("note", "고친 설명")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("closed"))
			.andExpect(jsonPath("$.definition_version").value(2));
	}

	@Test
	void AC58_같은_정의를_다시_저장하면_버전을_올리지_않는다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());

		patchForm(owner, formId, Map.of("title", "가상 신청")).andExpect(jsonPath("$.definition_version").value(1));

		assertThat(versionCount(formId)).isEqualTo(1);
	}

	@Test
	void AC56_되돌렸다_다시_열_때_같으면_같은_버전_다르면_다음_버전이다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		patchForm(owner, formId, Map.of("status", "draft")).andExpect(status().isOk())
			.andExpect(jsonPath("$.definition_version").value(1));
		publicById(formId).andExpect(status().isNotFound());

		patchForm(owner, formId, Map.of("status", "open")).andExpect(jsonPath("$.definition_version").value(1));

		patchForm(owner, formId, Map.of("status", "draft")).andExpect(status().isOk());
		patchForm(owner, formId, Map.of("title", "초안에서 고침")).andExpect(jsonPath("$.definition_version").value(1));
		publicById(formId).andExpect(status().isNotFound());
		patchForm(owner, formId, Map.of("status", "open")).andExpect(jsonPath("$.definition_version").value(2));
		assertThat(versionTitle(formId, 1)).isEqualTo("가상 신청");
	}

	@Test
	void AC56_되돌린_폼의_재배포가_실패해도_마지막_배포본은_그대로다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		patchForm(owner, formId, Map.of("status", "draft")).andExpect(status().isOk());
		Map<String, Object> noConsent = new LinkedHashMap<>();
		noConsent.put("consent_template_id", null);
		patchForm(owner, formId, noConsent).andExpect(status().isOk());

		expectError(patchForm(owner, formId, Map.of("status", "open")), 400, "consent_template_required");

		readDefinition(owner, formId).andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.definition_version").value(1));
		assertThat(versionCount(formId)).isEqualTo(1);
		assertThat(versionTitle(formId, 1)).isEqualTo("가상 신청");
	}

	@Test
	void AC57_허용되지_않는_전이는_invalid_transition이고_상태를_바꾸지_않는다() throws Exception {
		String draft = createDraft(SampleForms.unbranched());
		expectError(patchForm(owner, draft, Map.of("status", "closed")), 409, "invalid_transition");
		readDefinition(owner, draft).andExpect(jsonPath("$.status").value("draft"));

		String closed = createOpen(SampleForms.unbranched());
		patchForm(owner, closed, Map.of("status", "closed")).andExpect(status().isOk());
		expectError(patchForm(owner, closed, Map.of("status", "draft")), 409, "invalid_transition");
		readDefinition(owner, closed).andExpect(jsonPath("$.status").value("closed"));

		String answered = createOpen(SampleForms.unbranched());
		addSubmitted(answered, 1);
		expectError(patchForm(owner, answered, Map.of("status", "draft")), 409, "invalid_transition");
		readDefinition(owner, answered).andExpect(jsonPath("$.status").value("open"));
	}

	@Test
	void AC57_다시_열어도_share_url은_같다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		String first = definitionOf(formId).get("share_url").asString();

		patchForm(owner, formId, Map.of("status", "closed")).andExpect(jsonPath("$.share_url").value(first));
		patchForm(owner, formId, Map.of("status", "open")).andExpect(jsonPath("$.share_url").value(first));
		patchForm(owner, formId, Map.of("status", "draft")).andExpect(jsonPath("$.share_url").value(first));
		patchForm(owner, formId, Map.of("status", "open")).andExpect(jsonPath("$.share_url").value(first));
	}

	@Test
	void 응답이_있어도_정의_수정을_막지_않는다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		addSubmitted(formId, 2);

		patchForm(owner, formId, Map.of("title", "고침")).andExpect(status().isOk())
			.andExpect(jsonPath("$.definition_version").value(2));
	}

	private Map<String, Object> settingsWithLimit(Integer limit) {
		Map<String, Object> settings = SampleForms.settings(SampleForms.unbranched());
		settings.put("response_limit", limit);
		return settings;
	}

	@Test
	void 정수_범위를_넘는_인원은_schema_invalid다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.settings(definition).put("response_limit", 3_000_000_000L);

		expectError(postForm(owner, definition), 400, "schema_invalid");
	}

	@Test
	void AC85_인원이_제출_수_이하인_채로_다시_열면_response_limit_reached다() throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.settings(definition).put("response_limit", 2);
		String formId = createOpen(definition);
		addSubmitted(formId, 2);
		patchForm(owner, formId, Map.of("status", "closed")).andExpect(status().isOk());

		expectError(patchForm(owner, formId, Map.of("status", "open")), 409, "response_limit_reached");
		readDefinition(owner, formId).andExpect(jsonPath("$.status").value("closed"));

		Map<String, Object> raise = new LinkedHashMap<>();
		raise.put("status", "open");
		raise.put("settings", settingsWithLimit(3));
		patchForm(owner, formId, raise).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("open"));
	}

	@Test
	void AC85_open_폼의_인원을_제출_수_이하로_바꾸면_거부하고_바꾸지_않는다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		addSubmitted(formId, 2);

		expectError(patchForm(owner, formId, Map.of("settings", settingsWithLimit(2))), 409, "response_limit_reached");
		assertThat(jdbc.queryForObject("select response_limit from forms where form_id = ?::uuid", Integer.class, formId))
			.isNull();

		patchForm(owner, formId, Map.of("settings", settingsWithLimit(3))).andExpect(status().isOk())
			.andExpect(jsonPath("$.definition_version").value(1));
	}

	@Test
	void AC85_인원_없이_다시_여는_것은_허용한다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		addSubmitted(formId, 1);
		patchForm(owner, formId, Map.of("status", "closed")).andExpect(status().isOk());

		patchForm(owner, formId, Map.of("status", "open")).andExpect(status().isOk());
	}

	private Map<String, Object> aiSummaryOn() {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.settings(definition).put("ai_summary", Map.of("enabled", true));
		return definition;
	}

	private Map<String, Object> settingsWithAi(boolean enabled) {
		Map<String, Object> settings = SampleForms.settings(SampleForms.unbranched());
		settings.put("ai_summary", Map.of("enabled", enabled));
		return settings;
	}

	@Test
	void AC90_첫_배포_전에는_바꿀_수_있다() throws Exception {
		String formId = createDraft(aiSummaryOn());

		patchForm(owner, formId, Map.of("settings", settingsWithAi(false))).andExpect(status().isOk());
		patchForm(owner, formId, Map.of("settings", settingsWithAi(true))).andExpect(status().isOk());
	}

	@Test
	void AC90_첫_배포_뒤에는_되돌리거나_새_버전을_내도_바꿀_수_없다() throws Exception {
		String formId = createOpen(aiSummaryOn());
		JsonNode before = definitionOf(formId);

		Map<String, Object> flip = new LinkedHashMap<>();
		flip.put("settings", settingsWithAi(false));
		flip.put("title", "함께 보낸 수정");
		expectError(patchForm(owner, formId, flip), 400, "ai_summary_locked");
		assertThat(definitionOf(formId)).isEqualTo(before);

		patchForm(owner, formId, Map.of("status", "draft")).andExpect(status().isOk());
		expectError(patchForm(owner, formId, Map.of("settings", settingsWithAi(false))), 400, "ai_summary_locked");
		readDefinition(owner, formId).andExpect(jsonPath("$.status").value("draft"));

		// 같은 값은 바꾸는 요청이 아니다.
		patchForm(owner, formId, Map.of("settings", settingsWithAi(true))).andExpect(status().isOk());
	}

	@Test
	void AC90_꺼진_폼을_배포_뒤에_켤_수_없다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());

		expectError(patchForm(owner, formId, Map.of("settings", settingsWithAi(true))), 400, "ai_summary_locked");
	}

	@Test
	void AC90_고지는_첫_배포_때_기록하고_새_버전에서도_그대로다() throws Exception {
		String formId = createOpen(aiSummaryOn());
		String expected = JSON.writeValueAsString(ConsentNotices.aiSummaryNotice());

		publicById(formId).andExpect(MockMvcResultMatchers.jsonPath("$.consent.ai_summary.provider").value("OpenAI"))
			.andExpect(jsonPath("$.consent.ai_summary.checked_on").value("2026-10-28"))
			.andExpect(MockMvcResultMatchers.content()
				.json("{\"consent\":{\"ai_summary\":" + expected + "}}", JsonCompareMode.LENIENT));

		// 플랫폼의 고지가 바뀐 뒤를 흉내 낸다. 폼에 기록한 값이 공개되고 새 버전이 덮어쓰지 않는다.
		jdbc.update("update forms set ai_summary_notice = jsonb_set(ai_summary_notice, '{provider}', '\"기록된 제공사\"') "
				+ "where form_id = ?::uuid", formId);
		patchForm(owner, formId, Map.of("title", "새 버전")).andExpect(jsonPath("$.definition_version").value(2));

		publicById(formId).andExpect(jsonPath("$.consent.ai_summary.provider").value("기록된 제공사"));
	}

	@Test
	void AC90_AI_요약이_꺼진_폼은_고지가_null이다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());

		publicById(formId).andExpect(jsonPath("$.consent.ai_summary").value(nullValue()));
		assertThat(jdbc.queryForObject("select ai_summary_notice::text from forms where form_id = ?::uuid", String.class,
				formId))
			.isNull();
	}

	@Test
	void AC13_공개_조회는_SPEC_187행의_키와_동의_항목을_준다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());

		String body = publicById(formId).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

		JsonNode json = JSON.readTree(body);
		assertThat(json.propertyNames()).containsExactlyInAnyOrder("form_id", "definition_version", "title", "note",
				"note_attachments", "deadline", "sections", "display", "payment_link", "consent");
		assertThat(json.get("consent").propertyNames()).containsExactlyInAnyOrder("items", "purpose", "retention",
				"refusal_notice", "ai_summary");
		assertThat(json.get("consent").get("items").toString()).isEqualTo("[\"문항별 응답 내용\",\"이름\",\"휴대전화 번호\"]");
		assertThat(json.get("consent").get("refusal_notice").asString()).isEqualTo(ConsentNotices.REFUSAL_NOTICE);
		assertThat(json.get("consent").get("purpose").asString()).startsWith("폼을 만든 제작자가");
		assertThat(json.get("consent").get("retention").asString()).startsWith("정해진 보유 기간은 없습니다.")
			.contains("로그인하지 않고 제출한 경우: 직접 취소하거나 지울 수 없습니다.")
			.doesNotContain("\r");
		assertThat(json.get("display").get("allowed_modes").toString()).isEqualTo("[\"one_question\",\"all_questions\"]");
		assertThat(json.get("deadline").asString()).isEqualTo("2099-11-30T14:59:00Z");
		assertThat(body).doesNotContain("owner_id", "status", "slug", "share_url");
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "form_a", "123" })
	void UUID가_아닌_form_id는_404다(String formId) throws Exception {
		expectError(publicById(formId), 404, "not_found");
		expectError(readDefinition(owner, formId), 404, "not_found");
		expectError(patchForm(owner, formId, Map.of("title", "x")), 404, "not_found");
	}

	@Test
	void AC38_없는_폼과_초안은_공개_조회에서_404다() throws Exception {
		expectError(publicById(UUID.randomUUID().toString()), 404, "not_found");
		expectError(mockMvc.perform(get("/f/없는-슬러그")), 404, "not_found");

		String draft = createDraft(SampleForms.unbranched());
		expectError(publicById(draft), 404, "not_found");
		// 제작자가 공개 경로로 불러도 초안은 없는 폼이다(SPEC 187행).
		expectError(mockMvc.perform(get("/forms/" + draft).with(user(owner.toString()))), 404, "not_found");
	}

	@Test
	void AC38_삭제된_폼은_공개_조회와_제작자_API_모두_404다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());
		String slugPath = definitionOf(formId).get("share_url").asString().substring(frontendOrigin.length());
		jdbc.update("update forms set deleted_at = now() where form_id = ?::uuid", formId);

		expectError(publicById(formId), 404, "not_found");
		expectError(mockMvc.perform(get(slugPath)), 404, "not_found");
		expectError(readDefinition(owner, formId), 404, "not_found");
		expectError(patchForm(owner, formId, Map.of("title", "x")), 404, "not_found");
	}

	@Test
	void AC41_closed이거나_마감이_지난_폼은_form_closed다() throws Exception {
		String closed = createOpen(SampleForms.unbranched());
		String slugPath = definitionOf(closed).get("share_url").asString().substring(frontendOrigin.length());
		patchForm(owner, closed, Map.of("status", "closed")).andExpect(status().isOk());
		expectError(publicById(closed), 409, "form_closed");
		expectError(mockMvc.perform(get(slugPath)), 409, "form_closed");

		Map<String, Object> past = SampleForms.unbranched();
		past.put("deadline", "2020-01-01T00:00:00+09:00");
		String expired = createOpen(past);
		readDefinition(owner, expired).andExpect(jsonPath("$.status").value("open"));
		expectError(publicById(expired), 409, "form_closed");
	}

	@Test
	void 비로그인은_공개_조회만_되고_제작_API는_401이다() throws Exception {
		String formId = createOpen(SampleForms.unbranched());

		publicById(formId).andExpect(status().isOk());
		mockMvc.perform(get("/forms/" + formId + "/definition")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me/created-forms")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/consent-templates")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/forms").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/forms/" + formId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void CSRF_토큰이_없는_변경은_거부한다() throws Exception {
		mockMvc.perform(post("/forms").with(user(owner.toString()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(SampleForms.unbranched()))).andExpect(status().isForbidden());
	}

	@Test
	void AC2_다른_사용자는_정의를_읽거나_고칠_수_없다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());
		JsonNode before = definitionOf(formId);

		expectError(readDefinition(other, formId), 403, "forbidden");
		expectError(patchForm(other, formId, Map.of("title", "남의 수정")), 403, "forbidden");

		assertThat(definitionOf(formId)).isEqualTo(before);
	}

	@Test
	void AC39_내가_만든_폼만_최신순으로_상태와_응답_수를_준다() throws Exception {
		String oldest = createDraft(SampleForms.unbranched());
		String middle = createOpen(SampleForms.branched());
		String newest = createOpen(SampleForms.unbranched());
		patchForm(owner, newest, Map.of("status", "closed")).andExpect(status().isOk());
		addSubmitted(middle, 2);
		String deleted = createDraft(SampleForms.unbranched());
		jdbc.update("update forms set deleted_at = now() where form_id = ?::uuid", deleted);
		jdbc.update("update forms set created_at = created_at - interval '2 hour' where form_id = ?::uuid", oldest);
		jdbc.update("update forms set created_at = created_at - interval '1 hour' where form_id = ?::uuid", middle);
		String othersForm = JSON.readTree(postForm(other, SampleForms.unbranched()).andReturn().getResponse().getContentAsString())
			.get("form_id")
			.asString();

		String body = mockMvc.perform(get("/me/created-forms").with(user(owner.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].form_id").value(newest))
			.andExpect(jsonPath("$[0].status").value("closed"))
			.andExpect(jsonPath("$[0].response_count").value(0))
			.andExpect(jsonPath("$[1].form_id").value(middle))
			.andExpect(jsonPath("$[1].status").value("open"))
			.andExpect(jsonPath("$[1].title").value("가상 분기"))
			.andExpect(jsonPath("$[1].deadline").value(nullValue()))
			.andExpect(jsonPath("$[1].response_count").value(2))
			.andExpect(jsonPath("$[2].form_id").value(oldest))
			.andExpect(jsonPath("$[2].status").value("draft"))
			.andExpect(jsonPath("$[2].share_url").value(nullValue()))
			.andExpect(jsonPath("$[2].deadline").value("2099-11-30T14:59:00Z"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain(othersForm, deleted);
		List<String> keys = new ArrayList<>(JSON.readTree(body).get(0).propertyNames());
		assertThat(keys).containsExactlyInAnyOrder("form_id", "title", "status", "deadline", "share_url", "response_count");
	}

	@Test
	void AC39_만든_폼이_없으면_빈_목록이다() throws Exception {
		mockMvc.perform(get("/me/created-forms").with(user(other.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void GET_consent_templates는_consent_default를_준다() throws Exception {
		mockMvc.perform(get("/consent-templates").with(user(owner.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].consent_template_id").value("consent_default"))
			.andExpect(jsonPath("$[0].purpose").value(org.hamcrest.Matchers.startsWith("폼을 만든 제작자가")))
			.andExpect(jsonPath("$[0].retention").value(org.hamcrest.Matchers.containsString("자동으로 삭제되지 않습니다")));
	}

}
