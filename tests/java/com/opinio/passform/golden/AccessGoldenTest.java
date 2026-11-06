package com.opinio.passform.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.config.ClockConfig;
import com.opinio.passform.config.SecurityConfig;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.ConsentTemplateRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.NotificationRepository;
import com.opinio.passform.repository.ProfileRepository;
import com.opinio.passform.repository.QuestionTemplateRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.UserRepository;

/**
 * 접근 골든 테스트. tests/harness/golden_cases.yaml의 access_cases만 읽어 케이스마다 동적 테스트를 하나 만든다.
 * 케이스와 기대값은 YAML에만 둔다. MockMvc에 실제 SecurityConfig와 service, 모의 저장소를 붙인다(ARCHITECTURE G2).
 * 케이스에 있는 판정 키만 검사한다. POST 등 변경 요청에는 유효한 CSRF 토큰을 넣는다(CSRF 자체의 검사는 일반 테스트가 맡는다).
 * 지금은 사용자 행만 모의 저장소에 있다. 폼·응답을 읽는 케이스를 더하는 이슈(E5, E6)가 fixture 값을 저장소에 넣는다.
 * skip은 두 경우뿐이다: 스텁의 "not implemented", 그리고 보안 필터를 통과했는데 맞는 라우트가 없을 때(API 미구현).
 * 라우트가 없으면 MockMvc가 404를 주므로, skip하지 않으면 404 기대 케이스가 검사 없이 통과한다.
 * 보안 필터가 먼저 막은 요청(예: unauthenticated_401)은 라우트를 찾지 않으므로 skip하지 않는다.
 * 케이스를 더한 이슈는 자기 케이스의 skip 0을 완료 조건으로 삼는다.
 */
@WebMvcTest
@Import({ SecurityConfig.class, ClockConfig.class })
class AccessGoldenTest {

	private static final Path CASES = Path.of("tests/harness/golden_cases.yaml");

	// 스텁의 예외 메시지. 이 메시지의 UnsupportedOperationException만 skip으로 본다.
	private static final String NOT_IMPLEMENTED = "not implemented";

	private static final Set<String> JUDGE_KEYS = Set.of("expect_status", "expect_error", "must_not_contain");

	private static final Set<String> META_KEYS = Set.of("name", "ac", "type", "input", "note");

	private static final Set<String> INPUT_KEYS = Set.of("fixture", "actor", "method", "path", "body");

	private static final Set<String> ACTORS = Set.of("anonymous", "owner", "respondent", "other_user");

	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@TestConfiguration(proxyBeanMethods = false)
	@ComponentScan("com.opinio.passform.service")
	static class Services {

	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserRepository users;

	@MockitoBean
	private FormRepository forms;

	@MockitoBean
	private FormVersionRepository formVersions;

	@MockitoBean
	private ResponseRepository responses;

	@MockitoBean
	private ProfileRepository profiles;

	@MockitoBean
	private SavedFormRepository savedForms;

	@MockitoBean
	private NotificationRepository notifications;

	@MockitoBean
	private ConsentTemplateRepository consentTemplates;

	@MockitoBean
	private QuestionTemplateRepository questionTemplates;

	@MockitoBean
	private AttachmentRepository attachments;

	private final Map<UUID, User> actorUsers = new HashMap<>();

	@BeforeEach
	void setUpActors() {
		for (String actor : List.of("owner", "respondent", "other_user")) {
			actorUsers.put(uuidOf(actor), new User(actor, actor + "@example.com", "google", actor, Instant.EPOCH));
		}
		when(users.existsById(any(UUID.class))).thenAnswer(call -> actorUsers.containsKey(call.<UUID>getArgument(0)));
		when(users.findById(any(UUID.class)))
			.thenAnswer(call -> Optional.ofNullable(actorUsers.get(call.<UUID>getArgument(0))));
	}

	@Test
	void 골든_케이스_파일은_판정_가능한_형태다() throws IOException {
		List<String> problems = fileProblems(loadCases(Files.readString(CASES)));
		assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
	}

	@Test
	void 로더는_잘못된_케이스를_거부한다() {
		Map<String, Object> input = new LinkedHashMap<>(Map.of("fixture", "data/seed/forms/form_required_branch.json",
				"actor", "anonymous", "method", "GET", "path", "/me/forms"));
		Map<String, Object> valid = new LinkedHashMap<>(Map.of("name", "a", "ac", List.of("AC2"), "type", "금지 행동",
				"input", input, "note", "n", "expect_status", 401));
		assertEquals(List.of(), problemsOf(valid));

		assertRejected(with(valid, "expect_typo", 1), "허용 키 밖의 키");
		assertRejected(without(valid, "name"), "name 없음");
		assertRejected(without(valid, "expect_status"), "판정 키 없음");
		assertRejected(without(valid, "input"), "input 없음");
		assertRejected(with(valid, "input", with(input, "typo", 1)), "input의 허용 키 밖의 키");
		assertRejected(with(valid, "input", with(input, "fixture", "data/seed/forms/none.json")), "fixture 파일 없음");
		assertRejected(with(valid, "input", with(input, "actor", "admin")), "input.actor");
		assertRejected(with(valid, "input", with(input, "method", "TRACE")), "input.method");
		assertRejected(with(valid, "input", with(input, "path", "me/forms")), "input.path");
		assertRejected(with(valid, "expect_status", "401"), "expect_status");
		assertRejected(with(valid, "must_not_contain", "문자열"), "must_not_contain");

		assertTrue(fileProblems(List.of()).stream().anyMatch(problem -> problem.contains("비어 있다")));
		assertTrue(fileProblems(List.of(valid, valid)).stream().anyMatch(problem -> problem.contains("이름 중복")));
		assertTrue(thrownBy("access_cases:\n  - {name: a, name: b}\n") instanceof YAMLException, "YAML 중복 키");
	}

	@Test
	void 보안_필터를_통과했는데_라우트가_없으면_skip한다() {
		Map<String, Object> input = Map.of("actor", "other_user", "method", "GET", "path", "/no-such-route");
		TestAbortedException skipped = Assertions.assertThrows(TestAbortedException.class, () -> perform(input));
		assertTrue(skipped.getMessage().contains("라우트 미구현: GET /no-such-route"), skipped::getMessage);
	}

	@Test
	void 보안_필터가_막은_요청은_라우트가_없어도_skip하지_않는다() throws Exception {
		Map<String, Object> input = Map.of("actor", "anonymous", "method", "GET", "path", "/no-such-route");
		assertEquals(401, perform(input).getStatus());
	}

	@Test
	void 라우트가_있는_요청은_skip하지_않는다() throws Exception {
		Map<String, Object> input = Map.of("actor", "owner", "method", "GET", "path", "/me");
		assertEquals(200, perform(input).getStatus());
	}

	@TestFactory
	Stream<DynamicTest> 골든_케이스() throws IOException {
		return loadCases(Files.readString(CASES)).stream()
			.map(c -> DynamicTest.dynamicTest(String.valueOf(c.get("name")), () -> runCase(c)));
	}

	private void runCase(Map<String, Object> c) throws Exception {
		List<String> problems = problemsOf(c);
		assertTrue(problems.isEmpty(), () -> "케이스 형식 오류: " + problems);

		Map<String, Object> input = map(c.get("input"), "input");
		readFixture(Path.of(string(input.get("fixture"), "input.fixture")));
		// 기대값은 실행 전에 읽는다. 스텁 상태에서도 기대값 형식 오류는 skip이 아니라 실패로 드러난다.
		List<Consumer<MockHttpServletResponse>> checks = expectations(c);

		MockHttpServletResponse response = perform(input);
		assertAll(checks.stream().map(check -> (Executable) () -> check.accept(response)));
	}

	private MockHttpServletResponse perform(Map<String, Object> input) throws Exception {
		String method = string(input.get("method"), "input.method");
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders
			.request(HttpMethod.valueOf(method), string(input.get("path"), "input.path"));
		String actor = string(input.get("actor"), "input.actor");
		if (!"anonymous".equals(actor)) {
			request.with(user(uuidOf(actor).toString()));
		}
		if (!"GET".equals(method)) {
			request.with(csrf());
		}
		if (input.containsKey("body")) {
			request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(input.get("body")));
		}
		String target = method + " " + input.get("path");
		try {
			MvcResult result = mockMvc.perform(request).andReturn();
			skipIfNotImplemented(result.getResolvedException(), target);
			return result.getResponse();
		}
		catch (Exception e) {
			skipIfNotImplemented(e, target);
			throw e;
		}
	}

	// 공통 advice가 예외를 응답으로 바꾸므로 처리된 예외까지 원인을 따라가 미구현인지 본다.
	// skip 사유에 요청을 남긴다. 경로 오타나 잘못된 매핑도 같은 사유로 skip되므로 결과에서 찾을 수 있게 한다.
	private static void skipIfNotImplemented(Throwable thrown, String target) {
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			Assumptions.assumeFalse(
					cause instanceof UnsupportedOperationException && NOT_IMPLEMENTED.equals(cause.getMessage()),
					"스텁 미구현: " + target);
			Assumptions.assumeFalse(cause instanceof NoResourceFoundException || cause instanceof NoHandlerFoundException,
					() -> "라우트 미구현: " + target + " (API를 만들었다면 경로 오타나 매핑을 확인한다. 케이스를 더한 이슈는 skip 0을 확인한다)");
		}
	}

	private static List<Consumer<MockHttpServletResponse>> expectations(Map<String, Object> c) {
		List<Consumer<MockHttpServletResponse>> checks = new ArrayList<>();
		if (c.containsKey("expect_status")) {
			int expected = integer(c.get("expect_status"), "expect_status");
			checks.add(actual -> assertEquals(expected, actual.getStatus(), "expect_status"));
		}
		if (c.containsKey("expect_error")) {
			String expected = string(c.get("expect_error"), "expect_error");
			checks.add(actual -> assertEquals(expected, errorOf(actual), "expect_error"));
		}
		if (c.containsKey("must_not_contain")) {
			List<String> forbidden = strings(c.get("must_not_contain"), "must_not_contain");
			checks.add(actual -> forbidden
				.forEach(text -> assertFalse(body(actual).contains(text), () -> "must_not_contain: " + text)));
		}
		return checks;
	}

	private static Object errorOf(MockHttpServletResponse response) {
		return map(JSON.readValue(body(response), Object.class), "응답 본문").get("error");
	}

	private static String body(MockHttpServletResponse response) {
		try {
			return response.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			return fail("응답 본문을 읽지 못했다", e);
		}
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
			problems.add("access_cases가 비어 있다");
		}
		Set<String> names = new HashSet<>();
		for (int i = 0; i < cases.size(); i++) {
			Map<String, Object> c = cases.get(i);
			String label = "access_cases[" + i + "] " + c.get("name");
			if (c.get("name") instanceof String name && !names.add(name)) {
				problems.add(label + ": 케이스 이름 중복");
			}
			problemsOf(c).forEach(problem -> problems.add(label + ": " + problem));
		}
		return problems;
	}

	// 로더 검사와 케이스 실행이 함께 쓴다. 판단할 수 없는 입력은 문제로 돌려준다.
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
		if (c.containsKey("expect_status") && !(c.get("expect_status") instanceof Integer)) {
			problems.add("expect_status가 정수가 아니다");
		}
		if (c.containsKey("expect_error") && !(c.get("expect_error") instanceof String)) {
			problems.add("expect_error가 문자열이 아니다");
		}
		if (c.containsKey("must_not_contain") && !(c.get("must_not_contain") instanceof List<?>)) {
			problems.add("must_not_contain이 목록이 아니다");
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
		if (!ACTORS.contains(input.get("actor"))) {
			problems.add("input.actor를 모른다: " + input.get("actor"));
		}
		if (!METHODS.contains(input.get("method"))) {
			problems.add("input.method를 모른다: " + input.get("method"));
		}
		if (!(input.get("path") instanceof String path) || !path.startsWith("/")) {
			problems.add("input.path가 경로가 아니다: " + input.get("path"));
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
		List<?> cases = list(map(root, CASES.toString()).get("access_cases"), "access_cases");
		List<Map<String, Object>> result = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			result.add(map(cases.get(i), "access_cases[" + i + "]"));
		}
		return result;
	}

	// fixture가 읽히는 JSON이고 폼 ID가 있는지 본다.
	private static void readFixture(Path fixture) throws IOException {
		string(map(JSON.readValue(Files.readString(fixture), Object.class), fixture.toString()).get("form_id"),
				fixture + " form_id");
	}

	// actor 이름을 사용자 ID로 대응시킨다. 같은 이름은 항상 같은 UUID다.
	private static UUID uuidOf(String actor) {
		return UUID.nameUUIDFromBytes(actor.getBytes(StandardCharsets.UTF_8));
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
