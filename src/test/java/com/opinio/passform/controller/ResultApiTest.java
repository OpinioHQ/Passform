package com.opinio.passform.controller;

import static com.opinio.passform.support.TestData.NOW;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.SeedForms.Seeded;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

// GET /forms/{form_id}/results, GET /forms/{form_id}/responses/{response_id}를 실제 Postgres와 data/seed의 form_b로 시험한다.
// 인증은 모의 인증이고 이름은 users ID다(T2의 CurrentUserService가 읽는다). 기대값은 fixture를 손으로 센 값이다.
@Transactional
class ResultApiTest extends DatabaseTest {

	// form_b(data/seed/forms/form_b_mixed.json) 제출 14건(v1 6, v2 5, v3 3). rb15 in_progress, rb16 abandoned는 빠진다.
	private static final String FORM_B_RESULTS = """
			{"response_count":14,
			 "combined":{"questions":[
			  {"question_id":"q1","question_note":"소속 학과","question_type":"dropdown","form_versions":[1,2],"options":[
			   {"label":"소프트웨어학과","count":4,"form_versions":[1,2]},
			   {"label":"컴퓨터공학과","count":2,"form_versions":[1]},
			   {"label":"경영학과","count":4,"form_versions":[1,2]},
			   {"label":"컴퓨터공학부","count":1,"form_versions":[2]}]},
			  {"question_id":"q2","question_note":"학년","question_type":"multiple_choice","form_versions":[1],"options":[
			   {"label":"1학년","count":1,"form_versions":[1]},
			   {"label":"2학년","count":2,"form_versions":[1]},
			   {"label":"3학년","count":2,"form_versions":[1]},
			   {"label":"4학년","count":1,"form_versions":[1]}]},
			  {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","question_type":"multiple_choice","form_versions":[1,2,3],"options":[
			   {"label":"예","count":11,"form_versions":[1,2,3]},
			   {"label":"아니요","count":3,"form_versions":[1,2,3]}]},
			  {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","question_type":"multiple_choice","form_versions":[1,2,3],"options":[
			   {"label":"팀장","count":4,"form_versions":[1,2,3]},
			   {"label":"팀원","count":6,"form_versions":[1,2,3]}]},
			  {"question_id":"q3","question_note":"관심 분야","question_type":"checkbox","form_versions":[1,2,3],"options":[
			   {"label":"기획","count":6,"form_versions":[1,2,3]},
			   {"label":"디자인","count":4,"form_versions":[1,2,3]},
			   {"label":"개발","count":4,"form_versions":[1,2,3]},
			   {"label":"데이터 분석","count":2,"form_versions":[2,3]}]},
			  {"question_id":"q2","question_note":"현재 학년","question_type":"multiple_choice","form_versions":[2,3],"options":[
			   {"label":"1학년","count":2,"form_versions":[2,3]},
			   {"label":"2학년","count":1,"form_versions":[2,3]},
			   {"label":"3학년","count":2,"form_versions":[2,3]},
			   {"label":"4학년","count":3,"form_versions":[2,3]}]},
			  {"question_id":"q7","question_note":"희망 학과","question_type":"dropdown","form_versions":[3],"options":[
			   {"label":"데이터사이언스학과","count":1,"form_versions":[3]},
			   {"label":"경영학과","count":2,"form_versions":[3]}]}],
			 "text_questions":[{"question_id":"q4","question_note":"자기소개","question_type":"long_answer","form_versions":[1,2,3],"answers":%1$s}],
			 "question_order":[
			  {"question_id":"q1","question_note":"소속 학과","question_type":"dropdown"},
			  {"question_id":"q2","question_note":"학년","question_type":"multiple_choice"},
			  {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","question_type":"multiple_choice"},
			  {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","question_type":"multiple_choice"},
			  {"question_id":"q3","question_note":"관심 분야","question_type":"checkbox"},
			  {"question_id":"q4","question_note":"자기소개","question_type":"long_answer"},
			  {"question_id":"q2","question_note":"현재 학년","question_type":"multiple_choice"},
			  {"question_id":"q7","question_note":"희망 학과","question_type":"dropdown"}]},
			 "versions":[
			  {"form_version":1,"response_count":6,"questions":[
			   {"question_id":"q1","question_note":"소속 학과","counts":{"소프트웨어학과":2,"컴퓨터공학과":2,"경영학과":2}},
			   {"question_id":"q2","question_note":"학년","counts":{"1학년":1,"2학년":2,"3학년":2,"4학년":1}},
			   {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","counts":{"예":5,"아니요":1}},
			   {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","counts":{"팀장":2,"팀원":2}},
			   {"question_id":"q3","question_note":"관심 분야","counts":{"기획":4,"디자인":2,"개발":1}}],
			   "text_questions":[{"question_id":"q4","question_note":"자기소개","question_type":"long_answer","answers":%2$s}],
			   "question_order":[
			    {"question_id":"q1","question_note":"소속 학과","question_type":"dropdown"},
			    {"question_id":"q2","question_note":"학년","question_type":"multiple_choice"},
			    {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","question_type":"multiple_choice"},
			    {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","question_type":"multiple_choice"},
			    {"question_id":"q3","question_note":"관심 분야","question_type":"checkbox"},
			    {"question_id":"q4","question_note":"자기소개","question_type":"long_answer"}]},
			  {"form_version":2,"response_count":5,"questions":[
			   {"question_id":"q1","question_note":"소속 학과","counts":{"소프트웨어학과":2,"컴퓨터공학부":1,"경영학과":2}},
			   {"question_id":"q2","question_note":"현재 학년","counts":{"1학년":1,"2학년":0,"3학년":2,"4학년":2}},
			   {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","counts":{"예":4,"아니요":1}},
			   {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","counts":{"팀장":2,"팀원":2}},
			   {"question_id":"q3","question_note":"관심 분야","counts":{"기획":1,"디자인":1,"개발":2,"데이터 분석":1}}],
			   "text_questions":[{"question_id":"q4","question_note":"자기소개","question_type":"long_answer","answers":%3$s}],
			   "question_order":[
			    {"question_id":"q1","question_note":"소속 학과","question_type":"dropdown"},
			    {"question_id":"q2","question_note":"현재 학년","question_type":"multiple_choice"},
			    {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","question_type":"multiple_choice"},
			    {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","question_type":"multiple_choice"},
			    {"question_id":"q3","question_note":"관심 분야","question_type":"checkbox"},
			    {"question_id":"q4","question_note":"자기소개","question_type":"long_answer"}]},
			  {"form_version":3,"response_count":3,"questions":[
			   {"question_id":"q7","question_note":"희망 학과","counts":{"데이터사이언스학과":1,"경영학과":2}},
			   {"question_id":"q2","question_note":"현재 학년","counts":{"1학년":1,"2학년":1,"3학년":0,"4학년":1}},
			   {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","counts":{"예":2,"아니요":1}},
			   {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","counts":{"팀장":0,"팀원":2}},
			   {"question_id":"q3","question_note":"관심 분야","counts":{"기획":1,"디자인":1,"개발":1,"데이터 분석":1}}],
			   "text_questions":[{"question_id":"q4","question_note":"자기소개","question_type":"long_answer","answers":%4$s}],
			   "question_order":[
			    {"question_id":"q7","question_note":"희망 학과","question_type":"dropdown"},
			    {"question_id":"q2","question_note":"현재 학년","question_type":"multiple_choice"},
			    {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","question_type":"multiple_choice"},
			    {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","question_type":"multiple_choice"},
			    {"question_id":"q3","question_note":"관심 분야","question_type":"checkbox"},
			    {"question_id":"q4","question_note":"자기소개","question_type":"long_answer"}]}]}
			""";

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

	private UUID otherId;

	private Seeded formB;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		otherId = users.saveAndFlush(TestData.newUser()).getId();
		formB = SeedForms.load("form_b_mixed.json", ownerId, forms, formVersions, responses);
	}

	private ResultActions getAs(UUID userId, String path) throws Exception {
		return mockMvc.perform(get(path).with(user(userId.toString())));
	}

	private String resultsPath(UUID formId) {
		return "/forms/" + formId + "/results";
	}

	private String detailPath(UUID formId, Object responseId) {
		return "/forms/" + formId + "/responses/" + responseId;
	}

	// AC19, AC20, form_b 버전별 변경(data/seed/README.md): q1 선택지 문구 변경, q2 문구 변경, q3 선택지 추가, q7 새 문항
	@Test
	void form_b_결과는_통합과_버전별_통계를_돌려준다() throws Exception {
		getAs(ownerId, resultsPath(formB.formId())).andExpect(status().isOk())
			.andExpect(content().json(FORM_B_RESULTS.formatted(
					textAnswers("rb04", "rb12", "rb13", "rb08", "rb11", "rb01", "rb07", "rb10", "rb09", "rb05", "rb02",
							"rb14", "rb06", "rb03"),
					textAnswers("rb09", "rb05", "rb02", "rb14", "rb06", "rb03"),
					textAnswers("rb08", "rb11", "rb01", "rb07", "rb10"), textAnswers("rb04", "rb12", "rb13")),
					JsonCompareMode.STRICT));
	}

	// 새 필드(사람 결정 2026-11-09) 기대값: q4 자기소개 답. 순서는 fixture의 submitted_at을 손으로 내림차순 정렬한 것이고,
	// response_id·form_version·답·submitted_at은 fixture에서 읽는다.
	@SuppressWarnings("unchecked")
	private String textAnswers(String... seedIds) {
		Map<String, Object> seed;
		try {
			seed = JsonMapper.builder().build().readValue(Files.readString(Path.of("data/seed/forms/form_b_mixed.json")),
					Map.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		List<Map<String, Object>> answers = new ArrayList<>();
		for (String seedId : seedIds) {
			Map<String, Object> response = ((List<Map<String, Object>>) seed.get("responses")).stream()
				.filter(candidate -> seedId.equals(candidate.get("response_id")))
				.findFirst()
				.orElseThrow();
			Object value = ((List<Map<String, Object>>) response.get("answers")).stream()
				.filter(answer -> "q4".equals(answer.get("question_id")))
				.findFirst()
				.orElseThrow()
				.get("value");
			Map<String, Object> answer = new LinkedHashMap<>();
			answer.put("response_id", formB.response(seedId).toString());
			answer.put("form_version", response.get("form_version"));
			answer.put("value", value);
			answer.put("submitted_at", response.get("submitted_at"));
			answers.add(answer);
		}
		return JsonMapper.builder().build().writeValueAsString(answers);
	}

	// AC20: in_progress(rb15)·abandoned(rb16)·제작자 삭제 응답은 빠지고, 전체 수는 버전별 남은 submitted 수의 합이다.
	@Test
	void AC20_제작자가_삭제한_응답은_전체_수와_버전별_통계에서_빠진다() throws Exception {
		jdbc.update("update responses set creator_deleted_at = ? where response_id = ?", Timestamp.from(NOW),
				formB.response("rb01"));

		getAs(ownerId, resultsPath(formB.formId())).andExpect(status().isOk())
			.andExpect(jsonPath("$.response_count").value(13))
			.andExpect(jsonPath("$.versions[*].response_count").value(org.hamcrest.Matchers.contains(6, 4, 3)))
			.andExpect(jsonPath("$.versions[1].questions[4].counts['개발']").value(1));
	}

	@Test
	void 배포_전_폼은_빈_결과다() throws Exception {
		UUID draft = forms.saveAndFlush(new Form(ownerId, Map.of("title", "초안"), NOW)).getId();

		getAs(ownerId, resultsPath(draft)).andExpect(status().isOk())
			.andExpect(content().json("""
					{"response_count":0,"combined":{"questions":[],"text_questions":[],"question_order":[]},"versions":[]}
					""", JsonCompareMode.STRICT));
	}

	// AC62 200: 그 제출 버전의 문항 문구(v1 q2 "학년", 최신 v3은 "현재 학년"), 정의 순서, checkbox는 배열
	@Test
	void AC62_응답_상세는_제출_버전의_문구로_답을_돌려준다() throws Exception {
		getAs(ownerId, detailPath(formB.formId(), formB.response("rb02"))).andExpect(status().isOk())
			.andExpect(content().json("""
					{"response_id":"%s","form_version":1,"submitted_at":"2026-10-08T02:30:00Z","updated_at":null,
					 "content":[
					  {"question_id":"q1","question_note":"소속 학과","value":"소프트웨어학과"},
					  {"question_id":"q2","question_note":"학년","value":"3학년"},
					  {"question_id":"q5","question_note":"프로젝트 경험이 있나요?","value":"예"},
					  {"question_id":"q6","question_note":"프로젝트에서 맡은 역할","value":"팀원"},
					  {"question_id":"q3","question_note":"관심 분야","value":["기획"]},
					  {"question_id":"q4","question_note":"자기소개","value":"서비스 기획에 관심이 많습니다."}],
					 "attachments":[]}
					""".formatted(formB.response("rb02")), JsonCompareMode.STRICT));
	}

	// 분기로 건너뛴 q6(rb05)은 답한 문항이 아니라 content에 없다.
	@Test
	void 응답_상세에는_답한_문항만_있다() throws Exception {
		getAs(ownerId, detailPath(formB.formId(), formB.response("rb05"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].question_id").value(org.hamcrest.Matchers.contains("q1", "q2", "q5", "q3", "q4")));
		getAs(ownerId, detailPath(formB.formId(), formB.response("rb04"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].question_note").value("희망 학과"))
			.andExpect(jsonPath("$.content[1].question_note").value("현재 학년"));
	}

	// AC62 404: 다른 폼의 응답 ID
	@Test
	void AC62_다른_폼의_응답_ID는_404다() throws Exception {
		Seeded otherForm = SeedForms.load("form_b_mixed.json", ownerId, forms, formVersions, responses);

		getAs(ownerId, detailPath(formB.formId(), otherForm.response("rb02"))).andExpect(status().isNotFound())
			.andExpect(content().json("{\"error\":\"not_found\"}", JsonCompareMode.STRICT));
	}

	// AC20: 제출되지 않은 응답과 제작자가 삭제한 응답은 상세로도 볼 수 없다.
	@Test
	void 제출되지_않았거나_삭제된_응답의_상세는_404다() throws Exception {
		jdbc.update("update responses set creator_deleted_at = ? where response_id = ?", Timestamp.from(NOW),
				formB.response("rb01"));

		for (String seedId : List.of("rb15", "rb16", "rb01")) {
			getAs(ownerId, detailPath(formB.formId(), formB.response(seedId))).andExpect(status().isNotFound());
		}
		getAs(ownerId, detailPath(formB.formId(), UUID.randomUUID())).andExpect(status().isNotFound());
		getAs(ownerId, detailPath(formB.formId(), "rb02")).andExpect(status().isNotFound());
	}

	// AC62 403, AC2: 다른 사용자가 제작한 폼은 응답 내용 없이 403
	@Test
	void AC62_다른_사용자가_제작한_폼은_403이다() throws Exception {
		getAs(otherId, resultsPath(formB.formId())).andExpect(status().isForbidden())
			.andExpect(content().json("{\"error\":\"forbidden\"}", JsonCompareMode.STRICT));
		getAs(otherId, detailPath(formB.formId(), formB.response("rb02"))).andExpect(status().isForbidden())
			.andExpect(content().json("{\"error\":\"forbidden\"}", JsonCompareMode.STRICT));
		getAs(otherId, detailPath(formB.formId(), "not-a-uuid")).andExpect(status().isForbidden());
	}

	@Test
	void 없거나_삭제된_폼은_404다() throws Exception {
		jdbc.update("update forms set deleted_at = ? where form_id = ?", Timestamp.from(NOW), formB.formId());
		// JDBC로 바꾼 값을 읽도록 같은 트랜잭션의 1차 캐시를 비운다.
		entityManager.clear();

		for (UUID userId : List.of(ownerId, otherId)) {
			getAs(userId, resultsPath(formB.formId())).andExpect(status().isNotFound());
			getAs(userId, detailPath(formB.formId(), formB.response("rb02"))).andExpect(status().isNotFound());
		}
		getAs(ownerId, resultsPath(UUID.randomUUID())).andExpect(status().isNotFound());
		mockMvc.perform(get("/forms/not-a-uuid/results").with(user(ownerId.toString())))
			.andExpect(status().isNotFound());
	}

	@Test
	void 두_API는_비로그인이면_401이다() throws Exception {
		mockMvc.perform(get(resultsPath(formB.formId()))).andExpect(status().isUnauthorized());
		mockMvc.perform(get(detailPath(formB.formId(), formB.response("rb02")))).andExpect(status().isUnauthorized());
	}

}
