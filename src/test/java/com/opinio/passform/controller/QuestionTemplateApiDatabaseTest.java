package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.domain.template.QuestionTemplate;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.FormSchemaValidator;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.FormDefinitions;
import com.opinio.passform.support.TestData;

// GET /question-templates(SPEC 5절 113행, AC7·AC71). 데이터는 V3 초기 데이터(승인된 추천 문항 표)다.
class QuestionTemplateApiDatabaseTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormSchemaValidator schemaValidator;

	private MockMvc mockMvc;

	private UUID creator;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		creator = users.saveAndFlush(TestData.newUser()).getId();
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/question-templates").param("purpose", "apply").param("audience", "university"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 기본_정보를_먼저_그다음_고른_대상과_목적_공통_문항을_template_id_순으로_준다() throws Exception {
		assertThat(templateIds("apply", "university")).containsExactly("basic_01", "basic_02", "basic_03",
				"apply_all_03", "apply_all_04", "apply_univ_01", "apply_univ_02");
	}

	@Test
	void 같은_목적에서_대상을_바꾸면_그_대상의_문항으로_바뀐다() throws Exception {
		assertThat(templateIds("apply", "high_school")).containsExactly("basic_01", "basic_02", "basic_03",
				"apply_all_03", "apply_all_04", "apply_high_01", "apply_high_02");
		assertThat(templateIds("apply", "worker")).containsExactly("basic_01", "basic_02", "basic_03", "apply_all_03",
				"apply_all_04", "apply_work_01", "apply_work_02");
	}

	@Test
	void 누구나는_기본_정보와_목적_공통_문항만_받는다() throws Exception {
		assertThat(templateIds("quiz", "anyone")).containsExactly("basic_01", "basic_02", "basic_03", "quiz_all_02",
				"quiz_all_03", "quiz_all_04");
	}

	@Test
	void 기본_정보는_이름_휴대폰_번호_생년월일_형식이다() throws Exception {
		assertThat(noteToKind(fetch("survey", "worker"))).containsEntry("이름", "name")
			.containsEntry("휴대폰 번호", "mobile_phone")
			.containsEntry("생년월일", "date");
	}

	@Test
	void 예약을_고르면_예약자_성함과_예약_날짜를_이름_날짜_형식으로_추천한다() throws Exception {
		JsonNode body = fetch("reservation", "worker");

		assertThat(noteToKind(body)).containsEntry("예약자 성함", "name").containsEntry("예약 날짜", "date");
	}

	@Test
	void 항목에는_SPEC의_다섯_키만_있고_profile_key는_없다() throws Exception {
		for (String purpose : QuestionTemplate.PURPOSES) {
			for (String audience : QuestionTemplate.AUDIENCES) {
				for (JsonNode item : fetch(purpose, audience)) {
					assertThat(item.propertyNames()).containsExactlyInAnyOrder("template_id", "question_note",
							"question_type", "options", "input_format");
					assertThat(item.get("options").isArray()).isTrue();
				}
			}
		}
	}

	@Test
	void 모든_추천_문항은_profile_key_null로_추가하면_폼_스키마를_통과한다() throws Exception {
		for (String purpose : QuestionTemplate.PURPOSES) {
			for (String audience : QuestionTemplate.AUDIENCES) {
				List<Map<String, Object>> questions = new ArrayList<>();
				for (JsonNode item : fetch(purpose, audience)) {
					questions.add(questionFrom(item));
				}
				Map<String, Object> definition = new LinkedHashMap<>(
						FormDefinitions.definition("one_question", List.of(Map.of("section_id", "s1", "title", "", "questions", questions))));
				definition.put("note", "");
				definition.put("payment_link", null);
				definition.put("consent_template_id", "consent_default");
				definition.put("note_attachments", List.of());

				assertThatCode(() -> schemaValidator.validate(definition)).as(purpose + "/" + audience)
					.doesNotThrowAnyException();
			}
		}
	}

	@ParameterizedTest
	@CsvSource(value = { "unknown,university", "apply,unknown", "apply,all", "NULL,university", "apply,NULL" },
			nullValues = "NULL")
	void 목록_밖이거나_빠진_값은_400_invalid_request다(String purpose, String audience) throws Exception {
		var request = get("/question-templates").with(user(creator.toString()));
		if (purpose != null) {
			request.param("purpose", purpose);
		}
		if (audience != null) {
			request.param("audience", audience);
		}

		mockMvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
	}

	private JsonNode fetch(String purpose, String audience) throws Exception {
		String body = mockMvc
			.perform(get("/question-templates").param("purpose", purpose)
				.param("audience", audience)
				.with(user(creator.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JSON.readTree(body);
	}

	private List<String> templateIds(String purpose, String audience) throws Exception {
		List<String> ids = new ArrayList<>();
		fetch(purpose, audience).forEach(item -> ids.add(item.get("template_id").asString()));
		return ids;
	}

	private static Map<String, String> noteToKind(JsonNode body) {
		Map<String, String> kinds = new LinkedHashMap<>();
		body.forEach(item -> kinds.put(item.get("question_note").asString(),
				item.get("input_format").isNull() ? null : item.get("input_format").get("kind").asString()));
		return kinds;
	}

	// 화면이 추천 문항을 추가할 때와 같은 모양(AC7): 문구·유형·선택지·input_format 복사, profile_key는 null.
	private static Map<String, Object> questionFrom(JsonNode item) {
		Map<String, Object> question = new LinkedHashMap<>();
		question.put("question_id", item.get("template_id").asString());
		question.put("template_id", item.get("template_id").asString());
		question.put("question_note", item.get("question_note").asString());
		question.put("question_type", item.get("question_type").asString());
		question.put("options", JSON.convertValue(item.get("options"), List.class));
		question.put("required", false);
		if (!item.get("input_format").isNull()) {
			question.put("input_format", JSON.convertValue(item.get("input_format"), Map.class));
		}
		question.put("profile_key", null);
		question.put("attachments", List.of());
		question.put("branch_rules", List.of());
		return question;
	}

}
