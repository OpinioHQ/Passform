package com.opinio.passform.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.domain.analysis.ConditionEvaluator;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.Coverage;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.analysis.MatchMode;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.analysis.VersionCoverage;

/**
 * 조건 평가 함수 골든 테스트 (강의 5 C절 테스트 하니스).
 * tests/harness/golden_cases.yaml의 케이스마다 동적 테스트를 하나 만든다. 케이스와 기대값은 YAML에만 두고 여기에 복사하지 않는다.
 * 케이스에 있는 판정 키만 검사하며, 평가 함수가 미구현 스텁이면 그 케이스를 skip한다.
 */
class ConditionEvaluatorGoldenTest {

	private static final Path CASES = Path.of("tests/harness/golden_cases.yaml");

	// ConditionEvaluator 스텁의 예외 메시지. 이 메시지의 UnsupportedOperationException만 skip으로 본다.
	private static final String NOT_IMPLEMENTED = "not implemented";

	private static final Set<String> JUDGE_KEYS = Set.of("expect_count", "expect_response_ids",
			"expect_total_submitted_count", "expect_not_evaluable_count", "expect_not_evaluable_by_version",
			"expect_matched", "must_not_include");

	private static final Set<String> META_KEYS = Set.of("name", "ac", "type", "input", "note");

	private static final Set<String> INPUT_KEYS = Set.of("fixture", "context", "matching");

	// RequestContext의 여섯 필드(SPEC 5절 306행). 이 함수는 conditions·match만 쓴다.
	private static final Set<String> CONTEXT_KEYS = Set.of("conditions", "match", "operation", "sort", "target",
			"topic");

	private static final Set<String> MATCH_ENTRY_KEYS = Set.of("condition", "form_version", "question_id", "kind",
			"option");

	private static final Set<String> VERSION_COVERAGE_KEYS = Set.of("form_version", "response_count",
			"unavailable_conditions");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void 골든_케이스_파일은_판정_가능한_형태다() throws IOException {
		List<Map<String, Object>> cases = loadCases();
		List<String> problems = new ArrayList<>();
		if (cases.isEmpty()) {
			problems.add("cases가 비어 있다");
		}
		Set<String> names = new HashSet<>();
		for (int i = 0; i < cases.size(); i++) {
			Map<String, Object> c = cases.get(i);
			String label = "cases[" + i + "] " + c.get("name");
			if (c.get("name") instanceof String name && !names.add(name)) {
				problems.add(label + ": 케이스 이름 중복");
			}
			problemsOf(c).forEach(problem -> problems.add(label + ": " + problem));
		}
		assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
	}

	@TestFactory
	Stream<DynamicTest> 골든_케이스() throws IOException {
		return loadCases().stream()
			.map(c -> DynamicTest.dynamicTest(String.valueOf(c.get("name")), () -> runCase(c)));
	}

	private static void runCase(Map<String, Object> c) throws IOException {
		List<String> problems = problemsOf(c);
		assertTrue(problems.isEmpty(), () -> "케이스 형식 오류: " + problems);

		Map<String, Object> input = map(c.get("input"), "input");
		Map<String, Object> context = map(input.get("context"), "input.context");
		List<String> conditions = strings(context.get("conditions"), "input.context.conditions");
		MatchMode match = context.get("match") == null ? null
				: MatchMode.valueOf(string(context.get("match"), "input.context.match").toUpperCase(Locale.ROOT));
		List<ConditionMatch> matching = conditionMatches(input.get("matching"), "input.matching");
		List<ResponseRecord> responses = loadResponses(Path.of(string(input.get("fixture"), "input.fixture")));
		// 기대값은 평가 전에 읽는다. 스텁 상태에서도 기대값 형식 오류는 skip이 아니라 실패로 드러난다.
		List<Consumer<EvaluationResult>> checks = expectations(c);

		EvaluationResult actual = evaluate(conditions, match, matching, responses);
		assertNotNull(actual, "evaluate 결과가 null이다");
		assertAll(checks.stream().map(check -> (Executable) () -> check.accept(actual)));
	}

	private static EvaluationResult evaluate(List<String> conditions, MatchMode match, List<ConditionMatch> matching,
			List<ResponseRecord> responses) {
		try {
			return new ConditionEvaluator().evaluate(conditions, match, matching, responses);
		}
		catch (UnsupportedOperationException e) {
			Assumptions.assumeFalse(NOT_IMPLEMENTED.equals(e.getMessage()), "ConditionEvaluator 미구현");
			throw e;
		}
	}

	private static List<Consumer<EvaluationResult>> expectations(Map<String, Object> c) {
		List<Consumer<EvaluationResult>> checks = new ArrayList<>();
		if (c.containsKey("expect_count")) {
			int expected = integer(c.get("expect_count"), "expect_count");
			checks.add(actual -> assertEquals(expected, actual.count(), "expect_count"));
		}
		if (c.containsKey("expect_response_ids")) {
			List<String> expected = strings(c.get("expect_response_ids"), "expect_response_ids");
			checks.add(actual -> assertEquals(expected, actual.responseIds(), "expect_response_ids(순서 포함)"));
		}
		if (c.containsKey("expect_total_submitted_count")) {
			int expected = integer(c.get("expect_total_submitted_count"), "expect_total_submitted_count");
			checks.add(actual -> assertEquals(expected, coverage(actual).totalSubmittedCount(),
					"expect_total_submitted_count"));
		}
		if (c.containsKey("expect_not_evaluable_count")) {
			int expected = integer(c.get("expect_not_evaluable_count"), "expect_not_evaluable_count");
			checks.add(actual -> assertEquals(expected, coverage(actual).notEvaluableCount(),
					"expect_not_evaluable_count"));
		}
		if (c.containsKey("expect_not_evaluable_by_version")) {
			List<VersionCoverage> expected = versionCoverages(c.get("expect_not_evaluable_by_version"),
					"expect_not_evaluable_by_version");
			checks.add(actual -> assertEquals(expected, coverage(actual).notEvaluableByVersion(),
					"expect_not_evaluable_by_version(전체, 순서 포함)"));
		}
		if (c.containsKey("expect_matched")) {
			List<ConditionMatch> expected = conditionMatches(c.get("expect_matched"), "expect_matched");
			checks.add(actual -> assertEquals(expected, actual.matched(), "expect_matched(전체, 순서 포함)"));
		}
		if (c.containsKey("must_not_include")) {
			List<String> forbidden = strings(c.get("must_not_include"), "must_not_include");
			checks.add(actual -> {
				assertNotNull(actual.responseIds(), "must_not_include: response_ids가 null이다");
				assertEquals(List.of(), forbidden.stream().filter(actual.responseIds()::contains).toList(),
						"must_not_include");
			});
		}
		return checks;
	}

	private static Coverage coverage(EvaluationResult actual) {
		assertNotNull(actual.coverage(), "coverage가 null이다");
		return actual.coverage();
	}

	// 로더 검사와 케이스 실행이 함께 쓴다. 이름 중복은 파일 전체를 봐야 하므로 로더 검사에서만 본다.
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
		if (!(input.get("fixture") instanceof String fixture) || !Files.isRegularFile(Path.of(fixture))) {
			problems.add("input.fixture 파일 없음: " + input.get("fixture"));
		}
		if (input.get("context") instanceof Map<?, ?> context) {
			Set<String> unknownContext = keysOutside(context, CONTEXT_KEYS);
			if (!unknownContext.isEmpty()) {
				problems.add("input.context의 허용 키 밖의 키 " + unknownContext);
			}
		}
		else {
			problems.add("input.context 없음");
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

	private static List<Map<String, Object>> loadCases() throws IOException {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Object root = new Yaml(new SafeConstructor(options)).load(Files.readString(CASES));
		List<?> cases = list(map(root, CASES.toString()).get("cases"), "cases");
		List<Map<String, Object>> result = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			result.add(map(cases.get(i), "cases[" + i + "]"));
		}
		return result;
	}

	// fixture의 responses를 평가 함수 입력으로 바꾼다. 단일 값은 1개짜리 목록, 배열은 그대로, 비운 문항은 키 없음.
	private static List<ResponseRecord> loadResponses(Path fixture) throws IOException {
		Map<String, Object> root = map(JSON.readValue(Files.readString(fixture), Object.class), fixture.toString());
		List<?> entries = list(root.get("responses"), fixture + " responses");
		List<ResponseRecord> responses = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			String at = fixture + " responses[" + i + "]";
			Map<String, Object> response = map(entries.get(i), at);
			Map<String, List<String>> answers = new LinkedHashMap<>();
			for (Object entry : list(response.get("answers"), at + ".answers")) {
				Map<String, Object> answer = map(entry, at + ".answers[]");
				String questionId = string(answer.get("question_id"), at + ".answers[].question_id");
				Object value = answer.get("value");
				List<String> values = value instanceof List<?> ? strings(value, at + "." + questionId)
						: List.of(string(value, at + "." + questionId));
				if (answers.put(questionId, values) != null) {
					fail(at + ": question_id 중복 " + questionId);
				}
			}
			responses.add(new ResponseRecord(string(response.get("response_id"), at + ".response_id"),
					integer(response.get("form_version"), at + ".form_version"),
					ResponseStatus.valueOf(string(response.get("status"), at + ".status").toUpperCase(Locale.ROOT)),
					Collections.unmodifiableMap(answers)));
		}
		return List.copyOf(responses);
	}

	private static List<ConditionMatch> conditionMatches(Object value, String label) {
		List<?> entries = list(value, label);
		List<ConditionMatch> matches = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			String at = label + "[" + i + "]";
			Map<String, Object> entry = map(entries.get(i), at);
			assertEquals(MATCH_ENTRY_KEYS, entry.keySet(), at + "의 키");
			matches.add(new ConditionMatch(string(entry.get("condition"), at + ".condition"),
					integer(entry.get("form_version"), at + ".form_version"),
					string(entry.get("question_id"), at + ".question_id"),
					MatchKind.valueOf(string(entry.get("kind"), at + ".kind").toUpperCase(Locale.ROOT)),
					entry.get("option") == null ? null : string(entry.get("option"), at + ".option")));
		}
		return List.copyOf(matches);
	}

	private static List<VersionCoverage> versionCoverages(Object value, String label) {
		List<?> entries = list(value, label);
		List<VersionCoverage> coverages = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			String at = label + "[" + i + "]";
			Map<String, Object> entry = map(entries.get(i), at);
			assertEquals(VERSION_COVERAGE_KEYS, entry.keySet(), at + "의 키");
			coverages.add(new VersionCoverage(integer(entry.get("form_version"), at + ".form_version"),
					integer(entry.get("response_count"), at + ".response_count"),
					strings(entry.get("unavailable_conditions"), at + ".unavailable_conditions")));
		}
		return List.copyOf(coverages);
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

	private static int integer(Object value, String label) {
		return value instanceof Integer number ? number : fail(label + ": 정수가 아니다 (" + value + ")");
	}

}
