package com.opinio.passform.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

// FormGoldenTest의 로더 검사. 실행기 XML의 tests 수가 케이스 수와 같도록 별도 클래스에 둔다.
class FormGoldenLoaderTest {

	private static final Map<String, Object> INPUT = Map.of("fixture", "data/seed/forms/form_a_versions.json", "actor",
			"owner", "action", "open");

	private static final Map<String, Object> VALID = Map.of("name", "a", "ac", List.of("AC12"), "type", "금지 행동", "input",
			INPUT, "note", "n", "expect_error", "consent_template_required");

	@Test
	void 골든_케이스_파일은_판정_가능한_형태다() throws Exception {
		assertThat(FormGoldenTest.fileProblems(FormGoldenTest.loadCases(Files.readString(FormGoldenTest.CASES)))).isEmpty();
	}

	@Test
	void 유효한_케이스는_문제가_없다() {
		assertThat(FormGoldenTest.problemsOf(VALID)).isEmpty();
	}

	@Test
	void 잘못된_케이스를_거부한다() {
		assertRejected(with(VALID, "expect_typo", 1), "허용 키 밖의 키");
		assertRejected(without(VALID, "name"), "name 없음");
		assertRejected(without(VALID, "expect_error"), "판정 키 없음");
		assertRejected(without(VALID, "input"), "input 없음");
		assertRejected(with(VALID, "expect_error", 400), "expect_error");
		assertRejected(with(VALID, "expect_unchanged", "status"), "expect_unchanged");
		assertRejected(with(VALID, "expect_unchanged", List.of()), "expect_unchanged");
		assertRejected(with(VALID, "expect_unchanged", List.of("statuss")), "expect_unchanged의 키를 모른다");
		assertRejected(with(VALID, "input", with(INPUT, "typo", 1)), "input의 허용 키 밖의 키");
		assertRejected(with(VALID, "input", with(INPUT, "fixture", "data/seed/forms/none.json")), "input.fixture");
		assertRejected(with(VALID, "input", with(INPUT, "actor", "admin")), "input.actor");
		assertRejected(with(VALID, "input", with(INPUT, "action", "publish")), "input.action");
		assertRejected(with(VALID, "input", with(INPUT, "change", "text")), "input.change");
	}

	@Test
	void 파일_단위_문제를_찾는다() {
		assertThat(FormGoldenTest.fileProblems(List.of())).anyMatch(problem -> problem.contains("비어 있다"));
		assertThat(FormGoldenTest.fileProblems(List.of(VALID, VALID))).anyMatch(problem -> problem.contains("이름 중복"));
		assertThatThrownBy(() -> FormGoldenTest.loadCases("form_cases:\n  - {name: a, name: b}\n"))
			.isInstanceOf(YAMLException.class);
	}

	private static void assertRejected(Map<String, Object> c, String expectedProblem) {
		assertThat(FormGoldenTest.problemsOf(c)).anyMatch(problem -> problem.contains(expectedProblem));
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

}
