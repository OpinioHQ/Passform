package com.opinio.passform.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.SubmissionResult;
import com.opinio.passform.service.SubmissionService;
import com.opinio.passform.service.SubmitCommand;

/**
 * 제출 골든 테스트. tests/harness/golden_cases.yaml의 submission_cases만 읽어 케이스마다 동적 테스트를 하나 만든다.
 * 케이스와 기대값은 YAML에만 둔다. service 수준이고(ARCHITECTURE G2) 저장소는 메모리에 둔 모의 객체, 시계는 input.now로 고정한다.
 * 케이스에 있는 판정 키만 검사한다. expect_error가 없으면 오류 없이 끝나야 통과다.
 * fixture 해석(골든 머리 주석): status는 open, definition_version은 form_versions의 최대 번호.
 * deadline·response_limit은 최신 버전의 값을 forms 값으로 쓴다. consent_default는 제출 판정이 읽지 않아 준비할 것이 없다.
 */
class SubmissionGoldenTest {

	private static final Path CASES = Path.of("tests/harness/golden_cases.yaml");

	// SubmissionService 스텁의 예외 메시지. 이 메시지의 UnsupportedOperationException만 skip으로 본다.
	private static final String NOT_IMPLEMENTED = "not implemented";

	private static final Set<String> JUDGE_KEYS = Set.of("expect_error", "expect_question_ids", "expect_saved_answers",
			"expect_response_count");

	private static final Set<String> META_KEYS = Set.of("name", "ac", "type", "input", "note");

	private static final Set<String> INPUT_KEYS = Set.of("fixture", "now", "form_version", "answers", "consent_agreed",
			"ai_summary_agreed");

	private static final Set<String> ANSWER_KEYS = Set.of("question_id", "value");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void 골든_케이스_파일은_판정_가능한_형태다() throws IOException {
		List<Map<String, Object>> cases = loadCases(Files.readString(CASES));
		List<String> problems = fileProblems(cases);
		assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
	}

	@Test
	void 로더는_잘못된_케이스를_거부한다() {
		Map<String, Object> input = new LinkedHashMap<>(Map.of("fixture", "data/seed/forms/form_required_branch.json",
				"now", "2026-11-01T00:00:00Z", "form_version", 2, "answers", List.of()));
		Map<String, Object> valid = new LinkedHashMap<>(Map.of("name", "a", "ac", List.of("AC9"), "type", "정상", "input",
				input, "note", "n", "expect_response_count", 1));
		assertEquals(List.of(), problemsOf(valid));

		assertRejected(with(valid, "expect_typo", 1), "허용 키 밖의 키");
		assertRejected(without(valid, "name"), "name 없음");
		assertRejected(without(valid, "expect_response_count"), "판정 키 없음");
		assertRejected(with(valid, "input", with(input, "typo", 1)), "input의 허용 키 밖의 키");
		assertRejected(with(valid, "input", with(input, "fixture", "data/seed/forms/none.json")), "fixture 파일 없음");
		assertRejected(with(valid, "input", with(input, "now", "어제")), "input.now");
		assertRejected(with(valid, "input", with(input, "answers", List.of(Map.of("question_id", "q1", "typo", "x")))),
				"answers");

		assertTrue(fileProblems(List.of()).stream().anyMatch(problem -> problem.contains("비어 있다")));
		assertTrue(fileProblems(List.of(valid, valid)).stream().anyMatch(problem -> problem.contains("이름 중복")));
		assertTrue(thrownBy("submission_cases:\n  - {name: a, name: b}\n") instanceof YAMLException, "YAML 중복 키");
	}

	@TestFactory
	Stream<DynamicTest> 골든_케이스() throws IOException {
		return loadCases(Files.readString(CASES)).stream()
			.map(c -> DynamicTest.dynamicTest(String.valueOf(c.get("name")), () -> runCase(c)));
	}

	private static void runCase(Map<String, Object> c) throws IOException {
		List<String> problems = problemsOf(c);
		assertTrue(problems.isEmpty(), () -> "케이스 형식 오류: " + problems);

		Map<String, Object> input = map(c.get("input"), "input");
		FakeStore store = FakeStore.fromFixture(Path.of(string(input.get("fixture"), "input.fixture")));
		SubmitCommand command = new SubmitCommand(store.formId, null, integer(input.get("form_version"), "form_version"),
				answers(input.get("answers"), "input.answers"), bool(input.get("consent_agreed"), "consent_agreed"),
				bool(input.get("ai_summary_agreed"), "ai_summary_agreed"));
		Clock clock = Clock.fixed(Instant.parse(string(input.get("now"), "input.now")), ZoneOffset.UTC);
		// 기대값은 실행 전에 읽는다. 스텁 상태에서도 기대값 형식 오류는 skip이 아니라 실패로 드러난다.
		List<Consumer<Outcome>> checks = expectations(c);

		Outcome outcome = submit(store.service(clock), command, store);
		assertAll(checks.stream().map(check -> (Executable) () -> check.accept(outcome)));
	}

	private static Outcome submit(SubmissionService service, SubmitCommand command, FakeStore store) {
		try {
			SubmissionResult result = service.submit(command);
			assertNotNull(result, "submit 결과가 null이다");
			return new Outcome(null, store);
		}
		catch (UnsupportedOperationException e) {
			Assumptions.assumeFalse(NOT_IMPLEMENTED.equals(e.getMessage()), "SubmissionService 미구현");
			throw e;
		}
		catch (DomainException e) {
			return new Outcome(e, store);
		}
	}

	private static List<Consumer<Outcome>> expectations(Map<String, Object> c) {
		List<Consumer<Outcome>> checks = new ArrayList<>();
		String expectedError = c.containsKey("expect_error") ? string(c.get("expect_error"), "expect_error") : null;
		checks.add(actual -> assertEquals(expectedError, actual.errorCode(), "expect_error"));
		if (c.containsKey("expect_question_ids")) {
			List<String> expected = strings(c.get("expect_question_ids"), "expect_question_ids");
			checks.add(actual -> assertEquals(expected, actual.questionIds(), "expect_question_ids(순서 포함)"));
		}
		if (c.containsKey("expect_saved_answers")) {
			List<Answer> expected = answers(c.get("expect_saved_answers"), "expect_saved_answers");
			checks.add(actual -> assertEquals(expected, actual.store.onlySaved().getContent(),
					"expect_saved_answers(순서 포함)"));
		}
		if (c.containsKey("expect_response_count")) {
			int expected = integer(c.get("expect_response_count"), "expect_response_count");
			checks.add(actual -> assertEquals(expected, actual.store.submittedCount(), "expect_response_count"));
		}
		return checks;
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

	private static Throwable thrownBy(String yaml) {
		try {
			loadCases(yaml);
			return null;
		}
		catch (RuntimeException e) {
			return e;
		}
	}

	// 파일 전체를 봐야 아는 문제(비어 있음, 이름 중복)와 케이스별 문제를 모은다.
	private static List<String> fileProblems(List<Map<String, Object>> cases) {
		List<String> problems = new ArrayList<>();
		if (cases.isEmpty()) {
			problems.add("submission_cases가 비어 있다");
		}
		Set<String> names = new HashSet<>();
		for (int i = 0; i < cases.size(); i++) {
			Map<String, Object> c = cases.get(i);
			String label = "submission_cases[" + i + "] " + c.get("name");
			if (c.get("name") instanceof String name && !names.add(name)) {
				problems.add(label + ": 케이스 이름 중복");
			}
			problemsOf(c).forEach(problem -> problems.add(label + ": " + problem));
		}
		return problems;
	}

	// 로더 검사와 케이스 실행이 함께 쓴다.
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
		if (!isInstant(input.get("now"))) {
			problems.add("input.now가 시각이 아니다: " + input.get("now"));
		}
		if (!(input.get("form_version") instanceof Integer)) {
			problems.add("input.form_version이 정수가 아니다");
		}
		if (!(input.get("answers") instanceof List<?> answers)) {
			problems.add("input.answers 없음");
		}
		else if (answers.stream()
			.anyMatch(answer -> !(answer instanceof Map<?, ?> entry) || !keysOutside(entry, ANSWER_KEYS).isEmpty()
					|| !entry.containsKey("question_id"))) {
			problems.add("input.answers의 항목은 question_id, value만 쓴다");
		}
		return problems;
	}

	private static boolean isInstant(Object value) {
		if (!(value instanceof String text)) {
			return false;
		}
		try {
			Instant.parse(text);
			return true;
		}
		catch (RuntimeException e) {
			return false;
		}
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
		List<?> cases = list(map(root, CASES.toString()).get("submission_cases"), "submission_cases");
		List<Map<String, Object>> result = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			result.add(map(cases.get(i), "submission_cases[" + i + "]"));
		}
		return result;
	}

	private static List<Answer> answers(Object value, String label) {
		List<Answer> answers = new ArrayList<>();
		for (Object entry : list(value, label)) {
			Map<String, Object> answer = map(entry, label + "[]");
			answers.add(new Answer(string(answer.get("question_id"), label + "[].question_id"), answer.get("value")));
		}
		return List.copyOf(answers);
	}

	// fixture의 문자열 ID를 UUID로 대응시킨다. 같은 문자열은 항상 같은 UUID다.
	private static UUID uuidOf(String id) {
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException e) {
			return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
		}
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

	private static Boolean bool(Object value, String label) {
		if (value == null) {
			return null;
		}
		return value instanceof Boolean flag ? flag : fail(label + ": 불리언이 아니다 (" + value + ")");
	}

	private record Outcome(DomainException error, FakeStore store) {

		String errorCode() {
			return error == null ? null : error.getErrorCode().code();
		}

		Object questionIds() {
			return error == null ? null : error.getDetails().get("question_ids");
		}

	}

	// 케이스마다 fixture에서 새로 만드는 메모리 저장소. 저장소 인터페이스의 모의 객체가 이 값을 읽고 쓴다.
	private static final class FakeStore {

		private final UUID formId;

		private final Form form;

		private final Map<FormVersionId, FormVersion> formVersions = new HashMap<>();

		private final List<Response> stored = new ArrayList<>();

		private final List<Response> saved = new ArrayList<>();

		private FakeStore(UUID formId, Form form) {
			this.formId = formId;
			this.form = form;
		}

		static FakeStore fromFixture(Path fixture) throws IOException {
			Map<String, Object> root = map(JSON.readValue(Files.readString(fixture), Object.class), fixture.toString());
			UUID formId = uuidOf(string(root.get("form_id"), "form_id"));
			List<Map<String, Object>> versions = new ArrayList<>();
			for (Object entry : list(root.get("form_versions"), "form_versions")) {
				versions.add(map(entry, "form_versions[]"));
			}
			Map<String, Object> latest = versions.stream()
				.max(Comparator.comparingInt(version -> integer(version.get("form_version"), "form_version")))
				.orElseGet(() -> fail("form_versions가 비어 있다"));
			Map<String, Object> latestForm = map(latest.get("form"), "form");
			Map<String, Object> settings = map(latestForm.get("settings"), "settings");
			Instant createdAt = Instant.EPOCH;

			Form form = new Form(uuidOf("owner"), latestForm, createdAt);
			ReflectionTestUtils.setField(form, "id", formId);
			ReflectionTestUtils.setField(form, "status", FormStatus.OPEN);
			ReflectionTestUtils.setField(form, "definitionVersion", latest.get("form_version"));
			ReflectionTestUtils.setField(form, "deadline",
					latestForm.get("deadline") == null ? null : Instant.parse(string(latestForm.get("deadline"), "deadline")));
			ReflectionTestUtils.setField(form, "responseLimit", settings.get("response_limit"));

			FakeStore store = new FakeStore(formId, form);
			for (Map<String, Object> version : versions) {
				int number = integer(version.get("form_version"), "form_version");
				store.formVersions.put(new FormVersionId(formId, number),
						new FormVersion(formId, number, map(version.get("form"), "form"), createdAt));
			}
			for (Object entry : list(root.get("responses"), "responses")) {
				store.stored.add(response(formId, map(entry, "responses[]")));
			}
			return store;
		}

		private static Response response(UUID formId, Map<String, Object> entry) {
			Response response = new Response(formId, uuidOf("respondent"), integer(entry.get("form_version"), "form_version"),
					ResponseStatus.valueOf(string(entry.get("status"), "status").toUpperCase(Locale.ROOT)),
					answers(entry.get("answers"), "answers"), Boolean.TRUE.equals(entry.get("ai_summary_agreed")), null,
					entry.get("submitted_at") == null ? null : Instant.parse(string(entry.get("submitted_at"), "submitted_at")));
			ReflectionTestUtils.setField(response, "id", uuidOf(string(entry.get("response_id"), "response_id")));
			return response;
		}

		SubmissionService service(Clock clock) {
			FormRepository forms = mock(FormRepository.class);
			when(forms.findByIdForUpdate(formId)).thenReturn(Optional.of(form));
			when(forms.findById(formId)).thenReturn(Optional.of(form));
			when(forms.save(any(Form.class))).thenAnswer(call -> call.getArgument(0));

			FormVersionRepository versions = mock(FormVersionRepository.class);
			when(versions.findById(any(FormVersionId.class)))
				.thenAnswer(call -> Optional.ofNullable(formVersions.get(call.<FormVersionId>getArgument(0))));

			ResponseRepository responses = mock(ResponseRepository.class);
			when(responses.countSubmittedNotDeleted(formId)).thenAnswer(call -> (long) submittedCount());
			when(responses.save(any(Response.class))).thenAnswer(call -> store(call.getArgument(0)));
			when(responses.saveAndFlush(any(Response.class))).thenAnswer(call -> store(call.getArgument(0)));

			return new SubmissionService(forms, versions, responses, clock);
		}

		private Response store(Response response) {
			ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
			stored.add(response);
			saved.add(response);
			return response;
		}

		int submittedCount() {
			return (int) stored.stream()
				.filter(response -> response.getFormId().equals(formId))
				.filter(response -> response.getStatus() == ResponseStatus.SUBMITTED)
				.filter(response -> response.getCreatorDeletedAt() == null)
				.count();
		}

		Response onlySaved() {
			assertEquals(1, saved.size(), "저장된 응답 수");
			return saved.get(0);
		}

	}

}
