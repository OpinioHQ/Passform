package com.opinio.passform.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.opinio.passform.domain.analysis.ContactMasker;
import com.opinio.passform.domain.analysis.RagSettings;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.EmbeddingClient;
import com.opinio.passform.llm.LlmClient;
import com.opinio.passform.llm.LlmRequest;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.llm.RagConfiguration;
import com.opinio.passform.llm.SummaryWriter;
import com.opinio.passform.llm.ThemeClassifier;
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
 * 요약 골든 테스트. tests/harness/golden_cases.yaml의 summary_cases만 읽어 케이스마다 동적 테스트를 하나 만든다.
 * 기대값은 YAML에만 둔다. 실행 수준은 service이고 저장소와 외부 호출 경계(LlmClient, EmbeddingClient)만 모의 객체다(ARCHITECTURE G2).
 * 외부 전송 금지는 두 경계가 실제로 받은 요청 전체로 판정한다. 모의 경계는 요청받은 답의 labels·scores만 돌려주고, 표에 없는 답을 요청받으면 실패한다.
 */
class SummaryGoldenTest {

	private static final Path CASES = Path.of("tests/harness/golden_cases.yaml");

	private static final Path RESPONSE_SCHEMA = Path.of("src/response_analysis/schemas/analyze_response.schema.json");

	private static final Set<String> JUDGE_KEYS = Set.of("expect_summary_status", "expect_count", "expect_response_ids",
			"expect_excluded_no_consent_count", "llm_must_not_contain", "must_not_include");

	private static final Set<String> META_KEYS = Set.of("name", "ac", "type", "input", "note");

	private static final Set<String> INPUT_KEYS = Set.of("fixture", "context", "labels", "scores");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonMapper API_JSON = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	@Test
	void 골든_케이스_파일은_판정_가능한_형태다() throws IOException {
		List<Map<String, Object>> cases = loadCases();
		assertFalse(cases.isEmpty(), "summary_cases가 비어 있다");
		Set<String> names = new HashSet<>();
		for (Map<String, Object> c : cases) {
			assertTrue(names.add(String.valueOf(c.get("name"))), () -> "케이스 이름 중복: " + c.get("name"));
			List<String> problems = problemsOf(c);
			assertTrue(problems.isEmpty(), () -> c.get("name") + ": " + problems);
		}
	}

	@TestFactory
	Stream<DynamicTest> 골든_케이스() throws IOException {
		return loadCases().stream().map(c -> DynamicTest.dynamicTest(String.valueOf(c.get("name")), () -> runCase(c)));
	}

	private static void runCase(Map<String, Object> c) throws Exception {
		List<String> problems = problemsOf(c);
		assertTrue(problems.isEmpty(), () -> "케이스 형식 오류: " + problems);
		Map<String, Object> input = map(c.get("input"), "input");
		Scenario scenario = new Scenario(input);
		String body = JSON.writeValueAsString(Map.of("context", input.get("context")));

		JsonNode actual = API_JSON
			.valueToTree(scenario.service.analyze(new AnalyzeCommand(OWNER, scenario.formId.toString(), body)));
		assertResponseSchema(actual);
		assertEquals("ok", actual.path("status").asString(), () -> "status: " + actual);

		// 호출이 없으면 금지어 검사가 저절로 통과하므로 세 경계가 실제로 불렸는지 먼저 본다.
		if ("ok".equals(c.get("expect_summary_status"))) {
			assertTrue(scenario.classifyCalls > 0, "분류기를 부르지 않았다");
			assertTrue(scenario.embedCalls > 0, "임베딩을 부르지 않았다");
			assertTrue(scenario.writeCalls > 0, "요약문 작성을 부르지 않았다");
		}

		List<Executable> checks = new ArrayList<>();
		JsonNode result = actual.path("result");
		if (c.containsKey("expect_summary_status")) {
			checks.add(() -> assertEquals(c.get("expect_summary_status"), result.path("summary").path("status").asString(),
					"expect_summary_status"));
		}
		if (c.containsKey("expect_count")) {
			checks.add(() -> assertEquals(c.get("expect_count"), result.path("count").asInt(-1), "expect_count"));
		}
		if (c.containsKey("expect_response_ids")) {
			checks.add(() -> assertEquals(c.get("expect_response_ids"), scenario.seedIdsOf(actual), "expect_response_ids"));
		}
		if (c.containsKey("expect_excluded_no_consent_count")) {
			checks.add(() -> assertEquals(c.get("expect_excluded_no_consent_count"),
					result.path("summary").path("excluded_no_consent_count").asInt(-1), "expect_excluded_no_consent_count"));
		}
		if (c.containsKey("must_not_include")) {
			checks.add(() -> strings(c.get("must_not_include"))
				.forEach(id -> assertFalse(scenario.seedIdsOf(actual).contains(id), () -> "must_not_include: " + id)));
		}
		if (c.containsKey("llm_must_not_contain")) {
			checks.add(() -> strings(c.get("llm_must_not_contain")).forEach(value -> assertFalse(
					scenario.requestBodies.stream().anyMatch(sent -> sent.contains(value)),
					() -> "외부 요청 본문에 금지된 값이 있다: " + value)));
		}
		assertAll(checks);
	}

	private static void assertResponseSchema(JsonNode actual) throws IOException {
		try (InputStream schemaFile = Files.newInputStream(RESPONSE_SCHEMA)) {
			Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schemaFile);
			List<Error> errors = schema.validate(actual);
			assertTrue(errors.isEmpty(), () -> "응답 스키마 위반: " + errors + "\n" + actual);
		}
	}

	// fixture로 저장소를 채우고 외부 호출 경계만 모의 객체로 바꾼 service 한 벌.
	private static final class Scenario implements LlmClient, EmbeddingClient {

		private final AnalysisService service;

		private final UUID formId = seedUuid(0);

		private final Map<String, String> seedIdByUuid = new HashMap<>();

		// 외부로 나간 요청 전체. record의 문자열 표현에는 instructions, input, schema가 모두 들어 있다.
		private final List<String> requestBodies = new ArrayList<>();

		private final Map<String, String> answerKeyByMaskedText = new HashMap<>();

		private final Map<String, String> labels = new HashMap<>();

		private final Map<String, Double> scores = new HashMap<>();

		private int classifyCalls;

		private int embedCalls;

		private int writeCalls;

		private Scenario(Map<String, Object> input) throws IOException {
			Map<String, Object> fixture = map(
					JSON.readValue(Files.readString(Path.of((String) input.get("fixture"))), Object.class), "fixture");
			for (Object item : list(input.get("labels"))) {
				Map<String, Object> entry = map(item, "labels[]");
				labels.put(answerKey(entry.get("response_id"), entry.get("question_id")), (String) entry.get("label"));
			}
			for (Object item : list(input.get("scores"))) {
				Map<String, Object> entry = map(item, "scores[]");
				scores.put(answerKey(entry.get("response_id"), entry.get("question_id")),
						((Number) entry.get("score")).doubleValue());
			}

			FormVersionRepository formVersions = mock(FormVersionRepository.class);
			Map<FormVersionId, FormVersion> versions = new HashMap<>();
			int latest = 0;
			Map<String, Object> latestDefinition = Map.of();
			for (Object item : list(fixture.get("form_versions"))) {
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
			FormRepository forms = mock(FormRepository.class);
			when(forms.findById(formId)).thenReturn(Optional.of(form));

			ResponseRepository responses = mock(ResponseRepository.class);
			List<Response> submitted = submittedResponses(fixture);
			when(responses.findSubmittedNotDeletedByFormId(formId)).thenReturn(submitted);

			ConditionMatcher matcher = mock(ConditionMatcher.class);
			when(matcher.match(anyList(), anyList())).thenReturn(List.of());

			RagSettings settings = RagConfiguration.load();
			SummaryService summaries = new SummaryService(new ThemeClassifier(this, settings), new SummaryWriter(this), this,
					settings);
			service = new AnalysisService(forms, formVersions, responses, mock(QueryParser.class), matcher,
					new RequestContextSchema(), summaries);
		}

		private static String answerKey(Object responseId, Object questionId) {
			return responseId + "/" + questionId;
		}

		// 저장소 쿼리처럼 submitted만, 버전 → 제출 시각 → ID 순으로 돌려준다.
		private List<Response> submittedResponses(Map<String, Object> fixture) {
			List<Map<String, Object>> rows = new ArrayList<>();
			list(fixture.get("responses")).forEach(row -> rows.add(map(row, "responses[]")));
			List<String> seedIds = rows.stream().map(row -> (String) row.get("response_id")).sorted().toList();
			List<Response> submitted = new ArrayList<>();
			for (Map<String, Object> row : rows) {
				String seedId = (String) row.get("response_id");
				List<Answer> answers = new ArrayList<>();
				for (Object answer : list(row.get("answers"))) {
					Map<String, Object> entry = map(answer, "answers[]");
					answers.add(new Answer((String) entry.get("question_id"), entry.get("value")));
					if (entry.get("value") instanceof String text) {
						answerKeyByMaskedText.put(ContactMasker.mask(text), answerKey(seedId, entry.get("question_id")));
					}
				}
				if (!"submitted".equals(row.get("status"))) {
					continue;
				}
				UUID id = seedUuid(seedIds.indexOf(seedId) + 1);
				seedIdByUuid.put(id.toString(), seedId);
				Response response = new Response(formId, OWNER, (Integer) row.get("form_version"), ResponseStatus.SUBMITTED,
						answers, Boolean.TRUE.equals(row.get("ai_summary_agreed")), null,
						Instant.parse((String) row.get("submitted_at")));
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
			actual.path("result")
				.path("response_ids")
				.forEach(id -> ids.add(seedIdByUuid.getOrDefault(id.asString(), "시드에 없는 ID " + id.asString())));
			return ids;
		}

		private static UUID seedUuid(int index) {
			return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
		}

		@Override
		public String respond(LlmRequest request) {
			requestBodies.add(request.toString());
			if ("classify_themes".equals(request.purpose())) {
				classifyCalls++;
				return classify(request.input());
			}
			writeCalls++;
			return JSON.writeValueAsString(Map.of("text", "요약 문장"));
		}

		// 요청 본문의 <answers>에 있는 답에만 라벨을 준다.
		private String classify(String input) {
			String block = input.substring(input.indexOf("<answers>") + "<answers>".length(), input.indexOf("</answers>"));
			List<Map<String, String>> output = new ArrayList<>();
			for (JsonNode item : JSON.readTree(block)) {
				String answerKey = answerKeyByMaskedText.get(item.path("text").asString());
				if (answerKey == null || !labels.containsKey(answerKey)) {
					throw new AssertionError("모의 분류기: labels에 없는 답을 요청받았다: " + answerKey);
				}
				output.add(Map.of("key", item.path("key").asString(), "label", labels.get(answerKey)));
			}
			return JSON.writeValueAsString(Map.of("labels", output));
		}

		// 질의는 (1, 0), 답은 코사인이 그 답의 score가 되는 벡터다. 요청받은 문자열에만 벡터를 준다.
		@Override
		public List<double[]> embed(String purpose, List<String> texts) {
			embedCalls++;
			requestBodies.add(texts.toString());
			List<double[]> vectors = new ArrayList<>();
			for (String text : texts) {
				String answerKey = answerKeyByMaskedText.get(text);
				if (answerKey == null) {
					vectors.add(new double[] { 1, 0 });
					continue;
				}
				if (!scores.containsKey(answerKey)) {
					throw new AssertionError("모의 임베딩: scores에 없는 답을 요청받았다: " + answerKey);
				}
				double score = scores.get(answerKey);
				vectors.add(new double[] { score, Math.sqrt(1 - score * score) });
			}
			return vectors;
		}

	}

	private static List<String> problemsOf(Map<String, Object> c) {
		List<String> problems = new ArrayList<>();
		Set<String> unknown = keysOutside(c.keySet(), META_KEYS, JUDGE_KEYS);
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
		Set<String> unknownInput = keysOutside(input.keySet(), INPUT_KEYS);
		if (!unknownInput.isEmpty()) {
			problems.add("input의 허용 키 밖의 키 " + unknownInput);
		}
		if (!(input.get("fixture") instanceof String fixture) || !Files.isRegularFile(Path.of(fixture))) {
			problems.add("input.fixture 파일 없음");
		}
		if (!(input.get("context") instanceof Map<?, ?>)) {
			problems.add("input.context가 매핑이 아니다");
		}
		if (!(input.get("labels") instanceof List<?>) || !(input.get("scores") instanceof List<?>)) {
			problems.add("input.labels 또는 input.scores가 목록이 아니다");
		}
		return problems;
	}

	@SafeVarargs
	private static Set<String> keysOutside(Set<?> keys, Set<String>... allowed) {
		Set<String> outside = new TreeSet<>();
		keys.forEach(key -> outside.add(String.valueOf(key)));
		for (Set<String> set : allowed) {
			outside.removeAll(set);
		}
		return outside;
	}

	private static List<Map<String, Object>> loadCases() throws IOException {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Object root = new Yaml(new SafeConstructor(options)).load(Files.readString(CASES));
		List<Map<String, Object>> cases = new ArrayList<>();
		list(new LinkedHashMap<>(map(root, CASES.toString())).get("summary_cases"))
			.forEach(item -> cases.add(map(item, "summary_cases[]")));
		return cases;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value, String label) {
		return value instanceof Map<?, ?> ? (Map<String, Object>) value : fail(label + ": 매핑이 아니다");
	}

	private static List<?> list(Object value) {
		return value instanceof List<?> list ? list : fail("목록이 아니다: " + value);
	}

	private static List<String> strings(Object value) {
		return list(value).stream().map(String::valueOf).toList();
	}

}
