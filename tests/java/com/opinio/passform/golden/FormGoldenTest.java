package com.opinio.passform.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.repository.ConsentTemplateRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.FormSchemaValidator;
import com.opinio.passform.service.FormService;
import com.opinio.passform.service.FormService.DefinitionView;
import com.opinio.passform.service.FormService.UpdateCommand;
import com.opinio.passform.service.ShareUrls;

/**
 * 폼 골든 테스트. tests/harness/golden_cases.yaml의 form_cases만 읽어 케이스마다 동적 테스트를 하나 만든다.
 * 케이스와 기대값은 YAML에만 둔다. service 수준이며 저장소는 메모리 모의 객체다(ARCHITECTURE G2).
 * 각 케이스는 fixture의 최신 정의를 응답 없는 초안으로 만들어 시작한다(케이스 note, 이슈 #40).
 * 케이스에 있는 판정 키만 검사한다. expect_error가 없으면 오류 없이 끝나야 통과한다(ARCHITECTURE 192행).
 * 미구현을 skip으로 넘기지 않는다. 스텁이면 실패한다.
 */
class FormGoldenTest {

	static final Path CASES = Path.of("tests/harness/golden_cases.yaml");

	static final Set<String> JUDGE_KEYS = Set.of("expect_error", "expect_unchanged");

	private static final Set<String> META_KEYS = Set.of("name", "ac", "type", "input", "note");

	private static final Set<String> INPUT_KEYS = Set.of("fixture", "actor", "change", "action");

	private static final Set<String> ACTORS = Set.of("owner", "other_user");

	// action이 PATCH의 status 값이다. 이 이슈에서 판정 기준에 있는 동작만 받는다.
	private static final Map<String, String> ACTION_STATUS = Map.of("open", "open");

	// 폼 정의 밖의 값과 form.schema.json의 최상위 키. 없는 키는 항상 "null로 같음"이 되므로 거부한다.
	private static final Set<String> STATE_KEYS = Set.of("status", "share_url", "definition_version");

	private static final Set<String> DEFINITION_KEYS = Set.of("title", "note", "deadline", "settings", "payment_link",
			"consent_template_id", "note_attachments", "sections");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@TestFactory
	Stream<DynamicTest> 골든_케이스() throws IOException {
		List<Map<String, Object>> cases = loadCases(Files.readString(CASES));
		List<String> problems = fileProblems(cases);
		assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
		return cases.stream().map(c -> DynamicTest.dynamicTest(String.valueOf(c.get("name")), () -> runCase(c)));
	}

	private void runCase(Map<String, Object> c) throws IOException {
		Map<String, Object> input = map(c.get("input"), "input");
		Harness harness = new Harness();
		UUID owner = uuidOf("owner");
		String formId = harness.service
			.create(new FormService.CreateCommand(owner, latestDefinition(Path.of((String) input.get("fixture")))))
			.formId()
			.toString();
		// 조회 결과가 엔티티의 값을 참조할 수 있으므로 요청 전에 값을 복사해 둔다. 결함 주입으로 찾은 함정이다.
		Map<String, Object> before = snapshot(harness.service.getDefinition(owner, formId));
		assertEquals("draft", before.get("status"), "시작 상태는 응답 없는 초안이다");

		Map<String, Object> body = new LinkedHashMap<>(map(input.getOrDefault("change", Map.of()), "input.change"));
		body.put("status", ACTION_STATUS.get(input.get("action")));

		DomainException rejected = null;
		try {
			harness.service.update(new UpdateCommand(uuidOf((String) input.get("actor")), formId, body));
		}
		catch (DomainException e) {
			rejected = e;
		}

		Map<String, Object> after = snapshot(harness.service.getDefinition(owner, formId));
		List<Executable> checks = new ArrayList<>();
		String actualError = rejected == null ? null : rejected.getErrorCode().code();
		checks.add(() -> assertEquals(c.get("expect_error"), actualError, "expect_error"));
		if (c.containsKey("expect_unchanged")) {
			for (Object key : (List<?>) c.get("expect_unchanged")) {
				checks.add(() -> assertEquals(before.get(key), after.get(key), "expect_unchanged: " + key));
			}
		}
		assertAll(checks);
	}

	// 폼 정의와 상태 값을 한 매핑으로 깊은 복사한다.
	private static Map<String, Object> snapshot(DefinitionView view) {
		Map<String, Object> flat = new LinkedHashMap<>(view.definition());
		flat.put("status", view.status());
		flat.put("share_url", view.shareUrl());
		flat.put("definition_version", view.definitionVersion());
		return map(JSON.readValue(JSON.writeValueAsString(flat), Object.class), "조회 결과");
	}

	private static Map<String, Object> latestDefinition(Path fixture) throws IOException {
		Map<String, Object> root = map(JSON.readValue(Files.readString(fixture), Object.class), fixture.toString());
		return ((List<?>) root.get("form_versions")).stream()
			.map(version -> map(version, "form_versions[]"))
			.max(Comparator.comparingInt(version -> (Integer) version.get("form_version")))
			.map(version -> map(version.get("form"), "form"))
			.orElseGet(() -> fail(fixture + ": form_versions가 비어 있다"));
	}

	// 파일 전체를 봐야 아는 문제(비어 있음, 이름 중복)와 케이스별 문제를 모은다.
	static List<String> fileProblems(List<Map<String, Object>> cases) {
		List<String> problems = new ArrayList<>();
		if (cases.isEmpty()) {
			problems.add("form_cases가 비어 있다");
		}
		Set<String> names = new HashSet<>();
		for (int i = 0; i < cases.size(); i++) {
			Map<String, Object> c = cases.get(i);
			String label = "form_cases[" + i + "] " + c.get("name");
			if (c.get("name") instanceof String name && !names.add(name)) {
				problems.add(label + ": 케이스 이름 중복");
			}
			problemsOf(c).forEach(problem -> problems.add(label + ": " + problem));
		}
		return problems;
	}

	static List<String> problemsOf(Map<String, Object> c) {
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
		if (c.containsKey("expect_error") && !(c.get("expect_error") instanceof String)) {
			problems.add("expect_error가 문자열이 아니다");
		}
		if (c.containsKey("expect_unchanged")) {
			if (!(c.get("expect_unchanged") instanceof List<?> keys) || keys.isEmpty()) {
				problems.add("expect_unchanged가 비어 있지 않은 목록이 아니다");
			}
			else {
				keys.stream()
					.filter(key -> !STATE_KEYS.contains(key) && !DEFINITION_KEYS.contains(key))
					.forEach(key -> problems.add("expect_unchanged의 키를 모른다: " + key));
			}
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
		if (!ACTION_STATUS.containsKey(input.get("action"))) {
			problems.add("input.action을 모른다: " + input.get("action"));
		}
		if (input.containsKey("change") && !(input.get("change") instanceof Map<?, ?>)) {
			problems.add("input.change가 매핑이 아니다");
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

	static List<Map<String, Object>> loadCases(String yaml) {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Object root = new Yaml(new SafeConstructor(options)).load(yaml);
		Object cases = map(root, CASES.toString()).get("form_cases");
		if (!(cases instanceof List<?> list)) {
			return fail("form_cases: 목록이 아니다");
		}
		List<Map<String, Object>> result = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			result.add(map(list.get(i), "form_cases[" + i + "]"));
		}
		return result;
	}

	private static UUID uuidOf(String actor) {
		return UUID.nameUUIDFromBytes(actor.getBytes(StandardCharsets.UTF_8));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value, String label) {
		return value instanceof Map<?, ?> ? (Map<String, Object>) value : fail(label + ": 매핑이 아니다");
	}

	// 메모리 모의 저장소에 실제 service를 붙인다. 트랜잭션 되돌림이 없어 실패한 요청이 엔티티를 건드리면 그대로 드러난다.
	private static final class Harness {

		private final Form[] saved = new Form[1];

		private final FormService service;

		Harness() {
			FormRepository forms = mock(FormRepository.class);
			when(forms.save(any(Form.class))).thenAnswer(call -> {
				Form form = call.getArgument(0);
				if (form.getId() == null) {
					ReflectionTestUtils.setField(form, "id", UUID.randomUUID());
				}
				saved[0] = form;
				return form;
			});
			when(forms.findById(any(UUID.class))).thenAnswer(call -> Optional.ofNullable(saved[0]));
			when(forms.findByIdForUpdate(any(UUID.class))).thenAnswer(call -> Optional.ofNullable(saved[0]));
			// 시드의 consent_default는 fixture에 없어 실행기가 준비한다(data/seed/README.md 가정 7). 응답은 없다(모의 기본값 0건).
			ConsentTemplateRepository consentTemplates = mock(ConsentTemplateRepository.class);
			when(consentTemplates.existsById("consent_default")).thenReturn(true);
			service = new FormService(forms, mock(FormVersionRepository.class), mock(ResponseRepository.class),
					consentTemplates, new FormSchemaValidator(), new ShareUrls("https://app.example.com"),
					Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC));
		}

	}

}
