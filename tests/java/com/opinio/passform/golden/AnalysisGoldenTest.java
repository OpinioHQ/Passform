package com.opinio.passform.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.RequestContextSchema;
import com.opinio.passform.service.analysis.SummaryService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * 분석 골든 테스트. tests/harness/golden_cases.yaml의 analysis_cases만 읽어 케이스마다 동적 테스트를 하나 만든다.
 * 기대값은 YAML에만 둔다. 실행 수준은 service이고 저장소, 파서, 매처는 모의 객체다(ARCHITECTURE G2, G3).
 * 시드 ID는 UUID가 아니므로 시드 ID 순서를 유지하는 UUID로 바꿔 넣고, 결과를 시드 ID로 되돌려 순서까지 비교한다.
 */
class AnalysisGoldenTest {

	private static final Path CASES = Path.of("tests/harness/golden_cases.yaml");

	private static final Path RESPONSE_SCHEMA = Path.of("src/response_analysis/schemas/analyze_response.schema.json");

	// 사람이 승인한 skip이다. 분기 건너뜀과 응답 경로 계산은 E7(#45) 몫이고, E7이 이 목록을 지운다.
	private static final Set<String> SKIPPED_FOR_E7 = Set.of();

	private static final Set<String> APPROVED_SKIPS = Set.of();

	private static final Set<String> JUDGE_KEYS = Set.of("expect_error", "expect_reason", "expect_options",
			"expect_parser_calls", "expect_count", "expect_response_ids", "expect_not_evaluable_count",
			"expect_not_evaluable_by_version", "must_not_include");

	private static final Set<String> META_KEYS = Set.of("name", "ac", "type", "input", "note");

	private static final Set<String> INPUT_KEYS = Set.of("fixture", "query", "context", "parser_outputs", "matching");

	private static final Set<String> MATCH_ENTRY_KEYS = Set.of("condition", "form_version", "question_id", "kind",
			"option");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// 컨트롤러 응답과 같은 이름으로 직렬화한다(application.properties의 SNAKE_CASE).
	private static final JsonMapper API_JSON = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	@Test
	void 골든_케이스_파일은_판정_가능한_형태다() throws IOException {
		List<String> problems = fileProblems(loadCases(Files.readString(CASES)));
		assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
	}

	@Test
	void 로더는_잘못된_케이스를_거부한다() {
		Map<String, Object> input = new LinkedHashMap<>(Map.of("fixture", "data/seed/forms/form_a_versions.json", "query",
				"q", "parser_outputs", List.of(), "matching", List.of()));
		Map<String, Object> valid = new LinkedHashMap<>(Map.of("name", "a", "ac", List.of("AC22"), "type", "정상", "input",
				input, "note", "n", "expect_count", 1));
		assertEquals(List.of(), problemsOf(valid));

		assertRejected(with(valid, "expect_typo", 1), "허용 키 밖의 키");
		assertRejected(without(valid, "expect_count"), "판정 키 없음");
		assertRejected(with(valid, "input", with(input, "typo", 1)), "input의 허용 키 밖의 키");
		assertRejected(with(valid, "input", with(input, "context", Map.of())), "query와 context 중 하나");
		assertRejected(with(valid, "input", without(input, "query")), "query와 context 중 하나");
		assertRejected(with(valid, "input", without(input, "parser_outputs")), "parser_outputs");
		assertRejected(with(valid, "input", with(input, "fixture", "data/seed/forms/none.json")), "fixture 파일 없음");

		assertTrue(fileProblems(List.of()).stream().anyMatch(problem -> problem.contains("비어 있다")));
		assertTrue(fileProblems(List.of(valid, valid)).stream().anyMatch(problem -> problem.contains("이름 중복")));
	}

	@Test
	void skip_목록은_승인한_것뿐이고_모두_파일에_있다() throws IOException {
		assertEquals(APPROVED_SKIPS, SKIPPED_FOR_E7, "승인하지 않은 이름이 skip 목록에 들어갔다");
		Set<String> names = new HashSet<>();
		loadCases(Files.readString(CASES)).forEach(c -> names.add(String.valueOf(c.get("name"))));
		assertTrue(names.containsAll(SKIPPED_FOR_E7), "skip 목록의 케이스가 파일에 없다");
	}

	@Test
	void must_not_include는_response_ids_배열이_없으면_실패한다() {
		JsonNode countResult = JSON.readTree("{\"result\":{\"response_ids\":null}}");
		assertThrows(AssertionFailedError.class,
				() -> assertNoForbiddenIds(Map.of("must_not_include", List.of("rb01")), countResult, null));
	}

	@TestFactory
	Stream<DynamicTest> 골든_케이스() throws IOException {
		return loadCases(Files.readString(CASES)).stream()
			.map(c -> DynamicTest.dynamicTest(String.valueOf(c.get("name")), () -> runCase(c)));
	}

	private static void runCase(Map<String, Object> c) throws Exception {
		List<String> problems = problemsOf(c);
		assertTrue(problems.isEmpty(), () -> "케이스 형식 오류: " + problems);
		Assumptions.assumeFalse(SKIPPED_FOR_E7.contains(c.get("name")), "사람이 승인한 skip");

		Map<String, Object> input = map(c.get("input"), "input");
		Scenario scenario = Scenario.of(input);
		String body = JSON.writeValueAsString(
				input.containsKey("query") ? Map.of("query", input.get("query")) : Map.of("context", input.get("context")));
		AnalyzeCommand command = new AnalyzeCommand(OWNER, scenario.formId.toString(), body);

		JsonNode actual;
		try {
			actual = API_JSON.valueToTree(scenario.service.analyze(command));
		}
		catch (DomainException e) {
			assertEquals(c.get("expect_error"), e.getErrorCode().code(), "expect_error");
			assertParserCalls(c, scenario);
			return;
		}
		assertFalse(c.containsKey("expect_error"), () -> "expect_error: 오류 없이 끝났다 " + c.get("expect_error"));
		assertResponseSchema(actual);

		List<Executable> checks = new ArrayList<>();
		checks.add(() -> assertParserCalls(c, scenario));
		if (c.containsKey("expect_reason")) {
			checks.add(() -> assertEquals("clarify", actual.path("status").asString(), "expect_reason → status"));
			checks.add(() -> assertTrue(actual.path("result").isNull(), "expect_reason → result: null"));
			checks.add(() -> assertEquals(c.get("expect_reason"), actual.path("clarify").path("reason").asString(),
					"expect_reason"));
		}
		else {
			checks.add(() -> assertEquals("ok", actual.path("status").asString(), () -> "status: " + actual));
		}
		if (c.containsKey("expect_options")) {
			checks.add(() -> assertEquals(JSON.valueToTree(c.get("expect_options")), actual.path("clarify").path("options"),
					"expect_options"));
		}
		if (c.containsKey("expect_count")) {
			checks.add(() -> assertEquals(c.get("expect_count"), actual.path("result").path("count").asInt(-1),
					"expect_count"));
		}
		if (c.containsKey("expect_response_ids")) {
			checks.add(() -> assertEquals(c.get("expect_response_ids"), scenario.seedIdsOf(actual), "expect_response_ids"));
		}
		if (c.containsKey("must_not_include")) {
			checks.add(() -> assertNoForbiddenIds(c, actual, scenario));
		}
		if (c.containsKey("expect_not_evaluable_count")) {
			checks.add(() -> assertEquals(c.get("expect_not_evaluable_count"),
					actual.path("coverage").path("not_evaluable_count").asInt(-1), "expect_not_evaluable_count"));
		}
		if (c.containsKey("expect_not_evaluable_by_version")) {
			checks.add(() -> assertEquals(JSON.valueToTree(c.get("expect_not_evaluable_by_version")),
					actual.path("coverage").path("not_evaluable_by_version"), "expect_not_evaluable_by_version"));
		}
		assertAll(checks);
	}

	private static void assertNoForbiddenIds(Map<String, Object> c, JsonNode actual, Scenario scenario) {
		assertTrue(actual.path("result").path("response_ids").isArray(), "must_not_include: response_ids 배열이 없다");
		List<String> ids = scenario.seedIdsOf(actual);
		strings(c.get("must_not_include"), "must_not_include")
			.forEach(id -> assertFalse(ids.contains(id), () -> "must_not_include: " + id));
	}

	private static void assertParserCalls(Map<String, Object> c, Scenario scenario) {
		if (c.containsKey("expect_parser_calls")) {
			assertEquals(c.get("expect_parser_calls"), scenario.parserCalls[0], "expect_parser_calls");
		}
	}

	private static void assertResponseSchema(JsonNode actual) throws IOException {
		try (InputStream schemaFile = Files.newInputStream(RESPONSE_SCHEMA)) {
			Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schemaFile);
			List<Error> errors = schema.validate(actual);
			assertTrue(errors.isEmpty(), () -> "응답 스키마 위반: " + errors + "\n" + actual);
		}
	}

	// fixture로 모의 저장소·파서·매처를 채운 service 한 벌.
	private static final class Scenario {

		private final AnalysisService service;

		private final UUID formId;

		private final int[] parserCalls = { 0 };

		private final Map<String, String> seedIdByUuid = new HashMap<>();

		private Scenario(Map<String, Object> input) throws IOException {
			Map<String, Object> fixture = map(JSON.readValue(Files.readString(Path.of(string(input.get("fixture"), "fixture"))),
					Object.class), "fixture");
			formId = seedUuid(0);

			FormRepository forms = mock(FormRepository.class);
			FormVersionRepository formVersions = mock(FormVersionRepository.class);
			Map<FormVersionId, FormVersion> versions = new HashMap<>();
			int latest = 0;
			Map<String, Object> latestDefinition = Map.of();
			for (Object item : list(fixture.get("form_versions"), "form_versions")) {
				Map<String, Object> entry = map(item, "form_versions[]");
				int version = (Integer) entry.get("form_version");
				Map<String, Object> definition = map(entry.get("form"), "form");
				versions.put(new FormVersionId(formId, version), new FormVersion(formId, version, definition, Instant.EPOCH));
				if (version > latest) {
					latest = version;
					latestDefinition = definition;
				}
			}
			when(formVersions.findById(any(FormVersionId.class)))
				.thenAnswer(call -> Optional.ofNullable(versions.get(call.<FormVersionId>getArgument(0))));

			Form form = new Form(OWNER, latestDefinition, Instant.EPOCH);
			ReflectionTestUtils.setField(form, "id", formId);
			ReflectionTestUtils.setField(form, "definitionVersion", latest);
			when(forms.findById(formId)).thenReturn(Optional.of(form));

			ResponseRepository responses = mock(ResponseRepository.class);
			List<Response> submitted = submittedResponses(fixture);
			when(responses.findSubmittedNotDeletedByFormId(formId)).thenReturn(submitted);

			List<?> outputs = list(input.get("parser_outputs"), "parser_outputs");
			QueryParser parser = mock(QueryParser.class);
			when(parser.parse(anyString(), nullable(String.class))).thenAnswer(call -> {
				int index = parserCalls[0]++;
				if (index >= outputs.size()) {
					throw new AssertionError("모의 파서: parser_outputs보다 많이 불렀다");
				}
				return JSON.writeValueAsString(outputs.get(index));
			});

			List<ConditionMatch> matching = conditionMatches(input.get("matching"));
			ConditionMatcher matcher = mock(ConditionMatcher.class);
			when(matcher.match(anyList(), anyList())).thenReturn(matching);

			service = new AnalysisService(forms, formVersions, responses, parser, matcher, new RequestContextSchema(),
					mock(SummaryService.class));
		}

		private static Scenario of(Map<String, Object> input) throws IOException {
			return new Scenario(input);
		}

		// 저장소 쿼리처럼 submitted만, 버전 → 제출 시각 → ID 순으로 돌려준다. fixture의 다른 상태는 넣지 않는다.
		private List<Response> submittedResponses(Map<String, Object> fixture) {
			List<Map<String, Object>> rows = new ArrayList<>();
			list(fixture.get("responses"), "responses").forEach(row -> rows.add(map(row, "responses[]")));
			List<String> seedIds = rows.stream()
				.map(row -> string(row.get("response_id"), "response_id"))
				.sorted()
				.toList();
			List<Response> submitted = new ArrayList<>();
			for (Map<String, Object> row : rows) {
				if (!"submitted".equals(row.get("status"))) {
					continue;
				}
				List<Answer> answers = new ArrayList<>();
				for (Object answer : list(row.get("answers"), "answers")) {
					Map<String, Object> entry = map(answer, "answers[]");
					answers.add(new Answer(string(entry.get("question_id"), "question_id"), entry.get("value")));
				}
				String seedId = string(row.get("response_id"), "response_id");
				UUID id = seedUuid(seedIds.indexOf(seedId) + 1);
				seedIdByUuid.put(id.toString(), seedId);
				Response response = new Response(formId, OWNER, (Integer) row.get("form_version"), ResponseStatus.SUBMITTED,
						answers, Boolean.TRUE.equals(row.get("ai_summary_agreed")), null,
						Instant.parse(string(row.get("submitted_at"), "submitted_at")));
				ReflectionTestUtils.setField(response, "id", id);
				submitted.add(response);
			}
			submitted.sort(Comparator.comparing(Response::getFormVersion)
				.thenComparing(Response::getSubmittedAt)
				.thenComparing(Response::getId));
			return submitted;
		}

		private List<String> seedIdsOf(JsonNode actual) {
			List<String> ids = new ArrayList<>();
			actual.path("result").path("response_ids").forEach(id -> ids.add(seedIdByUuid.getOrDefault(id.asString(),
					"시드에 없는 ID " + id.asString())));
			return ids;
		}

		// 문자열 정렬과 UUID 정렬이 같아지게 뒷자리에 순번만 넣는다.
		private static UUID seedUuid(int index) {
			return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
		}

	}

	private static List<ConditionMatch> conditionMatches(Object value) {
		List<ConditionMatch> matches = new ArrayList<>();
		for (Object item : list(value, "matching")) {
			Map<String, Object> entry = map(item, "matching[]");
			matches.add(new ConditionMatch(string(entry.get("condition"), "condition"), (Integer) entry.get("form_version"),
					string(entry.get("question_id"), "question_id"),
					MatchKind.valueOf(string(entry.get("kind"), "kind").toUpperCase(Locale.ROOT)),
					(String) entry.get("option")));
		}
		return matches;
	}

	private static void assertRejected(Map<String, Object> c, String expectedProblem) {
		List<String> problems = problemsOf(c);
		assertTrue(problems.stream().anyMatch(problem -> problem.contains(expectedProblem)),
				() -> expectedProblem + " 문제가 나와야 한다: " + problems);
	}

	private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
		Map<String, Object> copy = new LinkedHashMap<>(base);
		copy.put(key, value);
		return copy;
	}

	private static Map<String, Object> without(Map<String, Object> base, String key) {
		Map<String, Object> copy = new LinkedHashMap<>(base);
		copy.remove(key);
		return copy;
	}

	private static List<String> fileProblems(List<Map<String, Object>> cases) {
		List<String> problems = new ArrayList<>();
		if (cases.isEmpty()) {
			problems.add("analysis_cases가 비어 있다");
		}
		Set<String> names = new HashSet<>();
		for (int i = 0; i < cases.size(); i++) {
			Map<String, Object> c = cases.get(i);
			String label = "analysis_cases[" + i + "] " + c.get("name");
			if (c.get("name") instanceof String name && !names.add(name)) {
				problems.add(label + ": 케이스 이름 중복");
			}
			problemsOf(c).forEach(problem -> problems.add(label + ": " + problem));
		}
		return problems;
	}

	private static List<String> problemsOf(Map<String, Object> c) {
		List<String> problems = new ArrayList<>();
		if (!(c.get("name") instanceof String name) || name.isBlank()) {
			problems.add("name 없음");
		}
		Set<String> unknown = keysOutside(c, META_KEYS, JUDGE_KEYS);
		if (!unknown.isEmpty()) {
			problems.add("허용 키 밖의 키 " + unknown);
		}
		if (c.keySet().stream().noneMatch(JUDGE_KEYS::contains)) {
			problems.add("판정 키 없음");
		}
		if (!(c.get("input") instanceof Map<?, ?> input)) {
			problems.add("input 없음");
			return problems;
		}
		Set<String> unknownInput = keysOutside(input, INPUT_KEYS);
		if (!unknownInput.isEmpty()) {
			problems.add("input의 허용 키 밖의 키 " + unknownInput);
		}
		if (input.containsKey("query") == input.containsKey("context")) {
			problems.add("input은 query와 context 중 하나만 쓴다");
		}
		if (!(input.get("fixture") instanceof String fixture) || !Files.isRegularFile(Path.of(fixture))) {
			problems.add("input.fixture 파일 없음: " + input.get("fixture"));
		}
		if (!(input.get("parser_outputs") instanceof List<?>)) {
			problems.add("input.parser_outputs가 목록이 아니다");
		}
		if (!(input.get("matching") instanceof List<?> matching)) {
			problems.add("input.matching이 목록이 아니다");
		}
		else {
			for (Object entry : matching) {
				if (!(entry instanceof Map<?, ?> match) || !keysOutside(match, MATCH_ENTRY_KEYS).isEmpty()) {
					problems.add("input.matching 항목의 허용 키 밖의 키: " + entry);
				}
			}
		}
		return problems;
	}

	@SafeVarargs
	private static Set<String> keysOutside(Map<?, ?> map, Set<String>... allowed) {
		Set<String> keys = new TreeSet<>();
		map.keySet().forEach(key -> keys.add(String.valueOf(key)));
		for (Set<String> set : allowed) {
			keys.removeAll(set);
		}
		return keys;
	}

	private static List<Map<String, Object>> loadCases(String yaml) {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Object root = new Yaml(new SafeConstructor(options)).load(yaml);
		List<?> cases = list(map(root, CASES.toString()).get("analysis_cases"), "analysis_cases");
		List<Map<String, Object>> result = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			result.add(map(cases.get(i), "analysis_cases[" + i + "]"));
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value, String label) {
		return value instanceof Map<?, ?> ? (Map<String, Object>) value : fail(label + ": 매핑이 아니다");
	}

	private static List<?> list(Object value, String label) {
		return value instanceof List<?> list ? list : fail(label + ": 목록이 아니다");
	}

	private static List<String> strings(Object value, String label) {
		List<String> strings = new ArrayList<>();
		for (Object item : list(value, label)) {
			strings.add(string(item, label + "[]"));
		}
		return List.copyOf(strings);
	}

	private static String string(Object value, String label) {
		return value instanceof String string ? string : fail(label + ": 문자열이 아니다 (" + value + ")");
	}

}
