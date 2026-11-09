package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.yaml.snakeyaml.Yaml;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.OpenAiClient;
import com.opinio.passform.llm.SummaryWriter;
import com.opinio.passform.llm.ThemeClassifier;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.FormSchemaValidator;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.SeedForms.Seeded;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// POST /forms/{form_id}/export를 실제 Postgres와 data/seed의 form_b(버전 3개)로 시험한다. LLM은 모의 객체다.
// 테스트 이름의 export_*·csv_*는 SPEC 추적표(627~629행)의 골든 id다. golden_cases.yaml에 없어 일반 테스트로 둔다.
@Transactional
class ExportApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String DEPARTMENT = "소프트웨어학과";

	// form_b의 q1은 v1·v2에만 있어 v3 응답 3건은 판정 불가다.
	private static final String LIST_CONTEXT = context("list");

	private static final List<String> VERSION_TIME_ORDER = List.of("rb03", "rb06", "rb14", "rb02", "rb05", "rb09", "rb10",
			"rb07", "rb01", "rb11", "rb08", "rb13", "rb12", "rb04");

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
	private FormSchemaValidator schemaValidator;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private ConditionMatcher matcher;

	@MockitoBean
	private OpenAiClient openAi;

	@MockitoBean
	private ThemeClassifier themeClassifier;

	@MockitoBean
	private SummaryWriter summaryWriter;

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
		when(matcher.match(anyList(), anyList())).thenReturn(List.of(
				new ConditionMatch(DEPARTMENT, 1, "q1", MatchKind.OPTION, DEPARTMENT),
				new ConditionMatch(DEPARTMENT, 2, "q1", MatchKind.OPTION, DEPARTMENT)));
	}

	private static String context(String operation) {
		return "{\"conditions\":[\"" + DEPARTMENT + "\"],\"match\":\"all\",\"operation\":\"" + operation + "\"}";
	}

	private static String filtered(String format, String context) {
		return "{\"format\":\"" + format + "\",\"context\":" + context + "}";
	}

	private MockHttpServletResponse export(UUID userId, UUID formId, String body) throws Exception {
		return mockMvc
			.perform(post("/forms/" + formId + "/export").with(user(userId.toString()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andReturn()
			.getResponse();
	}

	private MockHttpServletResponse exportB(String body) throws Exception {
		return export(ownerId, formB.formId(), body);
	}

	private JsonNode analyzeB(String context) throws Exception {
		MockHttpServletResponse response = mockMvc
			.perform(post("/forms/" + formB.formId() + "/analyze").with(user(ownerId.toString()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"context\":" + context + "}"))
			.andReturn()
			.getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		return JSON.readTree(utf8(response));
	}

	private static String utf8(MockHttpServletResponse response) throws Exception {
		return response.getContentAsString(StandardCharsets.UTF_8);
	}

	private JsonNode json(String body) throws Exception {
		MockHttpServletResponse response = exportB(body);
		assertThat(response.getStatus()).isEqualTo(200);
		return JSON.readTree(utf8(response));
	}

	private JsonNode yaml(String body) throws Exception {
		MockHttpServletResponse response = exportB(body);
		assertThat(response.getStatus()).isEqualTo(200);
		return yamlTree(utf8(response));
	}

	private static JsonNode yamlTree(String text) {
		Object parsed = new Yaml().load(text);
		return JSON.valueToTree(parsed);
	}

	private List<List<String>> csv(String body) throws Exception {
		MockHttpServletResponse response = exportB(body);
		assertThat(response.getStatus()).isEqualTo(200);
		return parseCsv(utf8(response));
	}

	// RFC 4180 왕복을 확인하려고 구현과 별개로 읽는다.
	private static List<List<String>> parseCsv(String text) {
		assertThat(text).startsWith("\uFEFF");
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else {
					cell.append(c);
				}
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			}
			else if (c == '\r') {
				assertThat(text.charAt(i + 1)).isEqualTo('\n');
				i++;
				row.add(cell.toString());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			}
			else {
				cell.append(c);
			}
		}
		assertThat(row).isEmpty();
		return rows;
	}

	private static String cell(List<List<String>> rows, String responseId, String column) {
		int index = rows.get(0).indexOf(column);
		assertThat(index).as(column).isGreaterThanOrEqualTo(0);
		return rows.stream().filter(row -> row.get(0).equals(responseId)).findFirst().orElseThrow().get(index);
	}

	private List<String> ids(List<String> seedIds) {
		return seedIds.stream().map(seedId -> formB.response(seedId).toString()).toList();
	}

	private static List<String> responseIdsOf(JsonNode document) {
		List<String> ids = new ArrayList<>();
		document.get("responses").forEach(response -> ids.add(response.get("response_id").asString()));
		return ids;
	}

	private static List<Integer> versionsOf(JsonNode document) {
		List<Integer> versions = new ArrayList<>();
		document.get("form_versions").forEach(version -> versions.add(version.get("form_version").asInt()));
		return versions;
	}

	private static List<String> csvIds(List<List<String>> rows) {
		return rows.stream().skip(1).map(row -> row.get(0)).toList();
	}

	private static String answerOf(JsonNode document, String responseId, String questionId) {
		for (JsonNode response : document.get("responses")) {
			if (response.get("response_id").asString().equals(responseId)) {
				for (JsonNode answer : response.get("answers")) {
					if (answer.get("question_id").asString().equals(questionId)) {
						return answer.get("value").asString();
					}
				}
			}
		}
		throw new AssertionError("답이 없다: " + questionId);
	}

	private UUID addSubmitted(UUID formId, int version, List<Answer> answers, Instant submittedAt) {
		return responses.saveAndFlush(Response.submitted(formId, null, version, answers, false, submittedAt)).getId();
	}

	private UUID formWithQuestions(Map<String, String> notes) {
		List<Map<String, Object>> questions = new ArrayList<>();
		notes.forEach((id, note) -> questions
			.add(Map.of("question_id", id, "question_note", note, "question_type", "short_answer")));
		Map<String, Object> definition = Map.of("sections", List.of(Map.of("section_id", "s1", "questions", questions)));
		UUID formId = forms.saveAndFlush(new Form(ownerId, definition, TestData.NOW)).getId();
		formVersions.saveAndFlush(new FormVersion(formId, 1, definition, TestData.NOW));
		return formId;
	}

	// AC48
	@Test
	void export_json_yaml_same_두_형식을_읽으면_같은_객체이고_폼_정의가_유효하다() throws Exception {
		JsonNode json = json("{\"format\":\"json\"}");
		JsonNode yaml = yaml("{\"format\":\"yaml\"}");

		assertThat(yaml).isEqualTo(json);
		assertThat(versionsOf(json)).containsExactly(1, 2, 3);
		for (JsonNode version : json.get("form_versions")) {
			int number = version.get("form_version").asInt();
			Map<String, Object> stored = formVersions.findById(new FormVersionId(formB.formId(), number))
				.orElseThrow()
				.getDefinition();
			assertThat(version.get("form")).isEqualTo(JSON.valueToTree(stored));
			schemaValidator.validate(JSON.treeToValue(version.get("form"), Map.class));
		}
		assertThat(json.get("form_versions").get(0).toString()).contains("\"학년\"");
		assertThat(json.get("form_versions").get(2).toString()).contains("\"현재 학년\"").doesNotContain("\"소속 학과\"");
	}

	// AC48, AC20: 응답마다 출처 버전이 있고 파일 키는 SPEC 241행뿐이다. 판정 불가 범위 키도 없다.
	@Test
	void export_flat_responses_with_version_키와_행_순서가_SPEC과_같다() throws Exception {
		JsonNode json = json("{\"format\":\"json\"}");

		assertThat(json.propertyNames()).containsExactly("form_versions", "responses");
		assertThat(responseIdsOf(json)).isEqualTo(ids(VERSION_TIME_ORDER));
		JsonNode first = json.get("responses").get(0);
		assertThat(first.propertyNames()).containsExactly("response_id", "form_version", "submitted_at", "updated_at",
				"answers");
		assertThat(first.get("form_version").asInt()).isEqualTo(1);
		assertThat(first.get("submitted_at").asString()).isEqualTo("2026-10-05T07:00:00Z");
		assertThat(first.get("updated_at").isNull()).isTrue();
		assertThat(first.get("answers").get(0).propertyNames()).containsExactly("question_id", "value");
	}

	@Test
	void 제어문자가_든_답은_YAML을_읽어도_JSON과_같은_문자열이다() throws Exception {
		String escape = "앞\u001B뒤";
		String others = "\u0007종\u007F\u0080";
		UUID id = addSubmitted(formB.formId(), 1, List.of(new Answer("q1", escape), new Answer("q4", others)),
				Instant.parse("2026-10-11T00:00:00Z"));

		String raw = utf8(exportB("{\"format\":\"yaml\"}"));
		JsonNode json = json("{\"format\":\"json\"}");
		JsonNode yaml = yaml("{\"format\":\"yaml\"}");

		assertThat(raw).doesNotContain("!!binary");
		assertThat(yaml).isEqualTo(json);
		assertThat(answerOf(yaml, id.toString(), "q1")).isEqualTo(escape);
		assertThat(answerOf(yaml, id.toString(), "q4")).isEqualTo(others);
	}

	@Test
	void 값처럼_보이는_문자열_답과_시각은_YAML에서도_문자열이다() throws Exception {
		UUID id = addSubmitted(formB.formId(), 1,
				List.of(new Answer("q1", "0123"), new Answer("q2", "true"), new Answer("q5", "null"),
						new Answer("q6", "2026-01-01"), new Answer("q4", "1e3"), new Answer("q3", List.of("123", "false"))),
				Instant.parse("2026-10-11T00:00:00Z"));

		JsonNode json = json("{\"format\":\"json\"}");
		JsonNode yaml = yaml("{\"format\":\"yaml\"}");

		assertThat(yaml).isEqualTo(json);
		JsonNode row = java.util.stream.StreamSupport.stream(yaml.get("responses").spliterator(), false)
			.filter(response -> response.get("response_id").asString().equals(id.toString()))
			.findFirst()
			.orElseThrow();
		assertThat(row.get("submitted_at").isString()).isTrue();
		for (JsonNode answer : row.get("answers")) {
			if (answer.get("value").isArray()) {
				answer.get("value").forEach(item -> assertThat(item.isString()).isTrue());
			}
			else {
				assertThat(answer.get("value").isString()).as(answer.toString()).isTrue();
			}
		}
		assertThat(answerOf(yaml, id.toString(), "q5")).isEqualTo("null");
	}

	// AC48
	@Test
	void export_csv_rows_헤더_뒤에_응답이_한_행씩이고_없는_칸은_비어_있다() throws Exception {
		MockHttpServletResponse response = exportB("{\"format\":\"csv\"}");
		assertThat(response.getContentType()).startsWith("text/csv");
		byte[] raw = response.getContentAsByteArray();
		assertThat(new byte[] { raw[0], raw[1], raw[2] }).containsExactly(0xEF, 0xBB, 0xBF);
		List<List<String>> rows = parseCsv(utf8(response));

		assertThat(rows).hasSize(15);
		assertThat(rows.get(0).subList(0, 4)).containsExactly("response_id", "form_version", "submitted_at", "updated_at");
		assertThat(csvIds(rows)).isEqualTo(ids(VERSION_TIME_ORDER));
		String rb06 = formB.response("rb06").toString();
		String rb05 = formB.response("rb05").toString();
		String rb12 = formB.response("rb12").toString();
		assertThat(cell(rows, rb06, "1.q3 관심 분야")).isEqualTo("기획; 디자인");
		assertThat(cell(rows, rb05, "1.q6 프로젝트에서 맡은 역할")).isEmpty();
		assertThat(cell(rows, rb06, "updated_at")).isEmpty();
		assertThat(cell(rows, rb12, "3.q7 희망 학과")).isEqualTo("경영학과");
		assertThat(cell(rows, rb12, "1.q1 소속 학과")).isEmpty();
	}

	// AC48: 같은 question_id라도 버전마다 제출 당시 문구로 열을 나눈다.
	@Test
	void export_form_versions_separate_헤더는_버전별_제출_당시_문구다() throws Exception {
		List<String> header = csv("{\"format\":\"csv\"}").get(0);

		assertThat(header).contains("1.q2 학년", "2.q2 현재 학년", "3.q2 현재 학년", "3.q7 희망 학과");
		assertThat(header).doesNotContain("1.q2 현재 학년", "1.q7 희망 학과", "3.q1 소속 학과");
	}

	// AC50
	@Test
	void csv_formula_escaped_수식처럼_시작하는_답은_작은따옴표를_붙인다() throws Exception {
		UUID plus = addSubmitted(formB.formId(), 1, List.of(new Answer("q4", "+1+2")), Instant.parse("2026-10-11T00:00:00Z"));
		UUID minus = addSubmitted(formB.formId(), 1, List.of(new Answer("q4", "-1")), Instant.parse("2026-10-11T01:00:00Z"));
		UUID at = addSubmitted(formB.formId(), 1, List.of(new Answer("q4", "@cmd")), Instant.parse("2026-10-11T02:00:00Z"));
		UUID checkbox = addSubmitted(formB.formId(), 1, List.of(new Answer("q3", List.of("=A", "B"))),
				Instant.parse("2026-10-11T03:00:00Z"));

		MockHttpServletResponse response = exportB("{\"format\":\"csv\"}");
		List<List<String>> rows = parseCsv(utf8(response));

		String note = "3.q4 자기소개";
		assertThat(cell(rows, formB.response("rb12").toString(), note)).startsWith("'=HYPERLINK(\"https://example.com/profile\"");
		assertThat(cell(rows, plus.toString(), "1.q4 자기소개")).isEqualTo("'+1+2");
		assertThat(cell(rows, minus.toString(), "1.q4 자기소개")).isEqualTo("'-1");
		assertThat(cell(rows, at.toString(), "1.q4 자기소개")).isEqualTo("'@cmd");
		assertThat(cell(rows, checkbox.toString(), "1.q3 관심 분야")).isEqualTo("'=A; B");
	}

	@Test
	void 수식으로_시작하는_문항_문구도_헤더에서_막는다() throws Exception {
		UUID formId = formWithQuestions(Map.of("q1", "=SUM(1)"));
		addSubmitted(formId, 1, List.of(new Answer("q1", "답")), TestData.NOW);

		List<List<String>> rows = parseCsv(utf8(export(ownerId, formId, "{\"format\":\"csv\"}")));

		assertThat(rows.get(0)).contains("1.q1 '=SUM(1)");
	}

	@Test
	void 수식_방지는_JSON과_YAML의_답을_바꾸지_않는다() throws Exception {
		String rb12 = formB.response("rb12").toString();

		assertThat(answerOf(json("{\"format\":\"json\"}"), rb12, "q4")).startsWith("=HYPERLINK(");
		assertThat(answerOf(yaml("{\"format\":\"yaml\"}"), rb12, "q4")).startsWith("=HYPERLINK(");
	}

	@Test
	void 쉼표_따옴표_줄바꿈이_있는_답은_따옴표로_감싸_왕복한다() throws Exception {
		String tricky = "쉼표, 그리고 \"따옴표\"\n다음 줄\r\n끝";
		UUID id = addSubmitted(formB.formId(), 1, List.of(new Answer("q4", tricky)), Instant.parse("2026-10-11T00:00:00Z"));

		MockHttpServletResponse response = exportB("{\"format\":\"csv\"}");

		assertThat(utf8(response)).contains("\"쉼표, 그리고 \"\"따옴표\"\"\n다음 줄\r\n끝\"");
		assertThat(cell(parseCsv(utf8(response)), id.toString(), "1.q4 자기소개")).isEqualTo(tricky);
	}

	@Test
	void form_versions_범위의_버전만_내보낸다() throws Exception {
		formVersions.saveAndFlush(new FormVersion(formB.formId(), 4,
				formVersions.findById(new FormVersionId(formB.formId(), 3)).orElseThrow().getDefinition(), TestData.NOW));

		JsonNode json = json("{\"format\":\"json\",\"form_versions\":[3,1,4]}");
		List<List<String>> rows = csv("{\"format\":\"csv\",\"form_versions\":[1,3]}");

		assertThat(versionsOf(json)).containsExactly(1, 3);
		assertThat(responseIdsOf(json)).isEqualTo(ids(List.of("rb03", "rb06", "rb14", "rb02", "rb05", "rb09", "rb13", "rb12", "rb04")));
		assertThat(rows).hasSize(10);
		assertThat(rows.get(0)).noneMatch(column -> column.startsWith("2."));
	}

	@Test
	void 잘못된_입력은_400_invalid_request다() throws Exception {
		for (String body : List.of("{\"format\":\"xml\"}", "{}", "{\"format\":1}", "{깨진 본문", "[]",
				"{\"format\":\"csv\",\"form_versions\":[]}", "{\"format\":\"csv\",\"form_versions\":[99]}",
				"{\"format\":\"csv\",\"form_versions\":\"1\"}", "{\"format\":\"csv\",\"form_versions\":[1.5]}",
				"{\"format\":\"csv\",\"query\":\"전부\"}")) {
			MockHttpServletResponse response = exportB(body);
			assertThat(response.getStatus()).as(body).isEqualTo(400);
			assertThat(utf8(response)).as(body).isEqualTo("{\"error\":\"invalid_request\"}");
		}
	}

	@Test
	void form_versions와_context를_함께_보내면_400_invalid_context다() throws Exception {
		for (String range : List.of("[1]", "[]")) {
			MockHttpServletResponse response = exportB(
					"{\"format\":\"csv\",\"form_versions\":" + range + ",\"context\":" + LIST_CONTEXT + "}");
			assertThat(response.getStatus()).isEqualTo(400);
			assertThat(utf8(response)).isEqualTo("{\"error\":\"invalid_context\"}");
		}
	}

	@Test
	void 스키마에_맞지_않는_context는_400_invalid_context다() throws Exception {
		MockHttpServletResponse response = exportB(filtered("json", "{\"conditions\":\"문자열\",\"operation\":\"list\"}"));

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(utf8(response)).isEqualTo("{\"error\":\"invalid_context\"}");
	}

	@Test
	void 내보낼_응답이_없으면_빈_파일이다() throws Exception {
		UUID empty = formWithQuestions(Map.of("q1", "소감"));

		JsonNode json = JSON.readTree(utf8(export(ownerId, empty, "{\"format\":\"json\"}")));
		JsonNode yaml = yamlTree(utf8(export(ownerId, empty, "{\"format\":\"yaml\"}")));
		List<List<String>> rows = parseCsv(utf8(export(ownerId, empty, "{\"format\":\"csv\"}")));

		assertThat(json.get("form_versions").isEmpty()).isTrue();
		assertThat(json.get("responses").isEmpty()).isTrue();
		assertThat(yaml).isEqualTo(json);
		assertThat(rows).containsExactly(List.of("response_id", "form_version", "submitted_at", "updated_at"));
	}

	// AC49: list는 분석 순서(응답 ID 오름차순)이고 판정 불가인 버전 3은 빠진다.
	@Test
	void export_filtered_matches_analyze_list는_세_형식_모두_분석_순서와_같다() throws Exception {
		JsonNode analyzed = analyzeB(LIST_CONTEXT);
		List<String> expected = new ArrayList<>();
		analyzed.get("result").get("response_ids").forEach(id -> expected.add(id.asString()));
		assertThat(analyzed.get("coverage").get("not_evaluable_count").asInt()).isEqualTo(3);
		assertThat(expected).containsExactlyInAnyOrderElementsOf(ids(List.of("rb01", "rb02", "rb06", "rb08")));
		assertThat(expected).isSorted();

		JsonNode json = json(filtered("json", LIST_CONTEXT));
		JsonNode yaml = yaml(filtered("yaml", LIST_CONTEXT));
		List<List<String>> rows = csv(filtered("csv", LIST_CONTEXT));

		assertThat(responseIdsOf(json)).isEqualTo(expected);
		assertThat(responseIdsOf(yaml)).isEqualTo(expected);
		assertThat(csvIds(rows)).isEqualTo(expected);
		assertThat(versionsOf(json)).containsExactly(1, 2);
		assertThat(rows.get(0)).noneMatch(column -> column.startsWith("3."));
	}

	// AC49
	@Test
	void export_excludes_not_evaluable_판정_불가_응답은_파일에_없다() throws Exception {
		JsonNode json = json(filtered("json", context("count")));

		assertThat(responseIdsOf(json)).doesNotContainAnyElementsOf(ids(List.of("rb04", "rb12", "rb13")));
		assertThat(json.propertyNames()).containsExactly("form_versions", "responses");
		assertThat(json.toString()).doesNotContain("coverage").doesNotContain("not_evaluable");
	}

	// AC49: 버전 → 제출 시각 → ID 순서
	@Test
	void export_non_list_time_order_count는_버전과_제출_시각_순서다() throws Exception {
		List<String> expected = ids(List.of("rb06", "rb02", "rb01", "rb08"));

		assertThat(responseIdsOf(json(filtered("json", context("count"))))).isEqualTo(expected);
		assertThat(csvIds(csv(filtered("csv", context("count"))))).isEqualTo(expected);
	}

	@Test
	void summarize_내보내기는_요약_경로를_부르지_않고_시각_순서다() throws Exception {
		jdbc.update("""
				update form_versions set definition = jsonb_set(definition, '{settings,ai_summary}', '{"enabled":true}')
				where form_id = ?
				""", formB.formId());
		jdbc.update("update responses set ai_summary_agreed = true where form_id = ?", formB.formId());
		entityManager.clear();

		List<List<String>> rows = csv(filtered("csv", context("summarize")));

		assertThat(csvIds(rows)).isEqualTo(ids(List.of("rb06", "rb02", "rb01", "rb08")));
		verifyNoInteractions(openAi, themeClassifier, summaryWriter);
	}

	// AC49: clarify면 파일 없이 분석과 같은 clarify다.
	@Test
	void clarify이면_파일_없이_같은_clarify를_돌려준다() throws Exception {
		String context = "{\"conditions\":[\"" + DEPARTMENT + "\"],\"operation\":null}";
		JsonNode analyzed = analyzeB(context);

		for (String format : List.of("json", "yaml", "csv")) {
			MockHttpServletResponse response = exportB(filtered(format, context));
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
			JsonNode body = JSON.readTree(utf8(response));
			assertThat(body.propertyNames()).containsExactly("status", "clarify");
			assertThat(body.get("status").asString()).isEqualTo("clarify");
			assertThat(body.get("clarify")).isEqualTo(analyzed.get("clarify"));
		}
	}

	@Test
	void LLM이_실패하면_503_llm_unavailable이다() throws Exception {
		when(matcher.match(any(), any())).thenThrow(new DomainException(ErrorCode.LLM_UNAVAILABLE));

		MockHttpServletResponse response = exportB(filtered("csv", LIST_CONTEXT));

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(utf8(response)).isEqualTo("{\"error\":\"llm_unavailable\"}");
	}

	// AC20: rb15(in_progress)·rb16(abandoned)·제작자가 지운 응답은 없다.
	@Test
	void AC20_작성_중_포기_삭제된_응답은_내보내지_않는다() throws Exception {
		jdbc.update("update responses set creator_deleted_at = ? where response_id = ?", Timestamp.from(TestData.NOW),
				formB.response("rb01"));
		entityManager.clear();

		List<String> exported = responseIdsOf(json("{\"format\":\"json\"}"));

		assertThat(exported).hasSize(13).doesNotContainAnyElementsOf(ids(List.of("rb15", "rb16", "rb01")));
	}

	// AC74: 수정한 답과 updated_at이 파일에 반영되고 제출 버전·시각은 그대로다.
	@Test
	void AC74_수정된_답이_반영된다() throws Exception {
		Response rb02 = responses.findById(formB.response("rb02")).orElseThrow();
		rb02.replaceSubmittedAnswers(List.of(new Answer("q4", "수정한 자기소개")), TestData.NOW);
		responses.saveAndFlush(rb02);
		String id = rb02.getId().toString();

		JsonNode json = json("{\"format\":\"json\"}");
		List<List<String>> rows = csv("{\"format\":\"csv\"}");

		assertThat(answerOf(json, id, "q4")).isEqualTo("수정한 자기소개");
		assertThat(cell(rows, id, "1.q4 자기소개")).isEqualTo("수정한 자기소개");
		assertThat(cell(rows, id, "updated_at")).isEqualTo("2026-11-05T00:00:00Z");
		assertThat(cell(rows, id, "submitted_at")).isEqualTo("2026-10-08T02:30:00Z");
	}

	@Test
	void AC2_제작자가_아니면_403이고_응답_내용이_없다() throws Exception {
		for (String body : List.of("{\"format\":\"csv\"}", "{깨진 본문")) {
			MockHttpServletResponse response = export(otherId, formB.formId(), body);
			assertThat(response.getStatus()).isEqualTo(403);
			assertThat(utf8(response)).isEqualTo("{\"error\":\"forbidden\"}");
		}
	}

	@Test
	void 없거나_삭제된_폼은_404다() throws Exception {
		assertThat(export(ownerId, UUID.randomUUID(), "{\"format\":\"csv\"}").getStatus()).isEqualTo(404);
		jdbc.update("update forms set deleted_at = ? where form_id = ?", Timestamp.from(TestData.NOW), formB.formId());
		entityManager.clear();

		assertThat(exportB("{\"format\":\"csv\"}").getStatus()).isEqualTo(404);
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		int status = mockMvc
			.perform(post("/forms/" + formB.formId() + "/export").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"format\":\"csv\"}"))
			.andReturn()
			.getResponse()
			.getStatus();

		assertThat(status).isEqualTo(401);
	}

}
