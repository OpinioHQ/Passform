package com.opinio.passform.domain.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.support.SampleForms;

// 의미 검증(ARCHITECTURE F4, AC8, AC46). 판정 위치가 도메인이라 저장소 없이 시험한다.
class FormPublishValidatorTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static void assertRejected(Map<String, Object> definition, ErrorCode code) {
		assertThatThrownBy(() -> FormPublishValidator.validate(definition)).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(code);
	}

	private static Map<String, Object> rule(String conditionQuestion, String option, String goTo) {
		return SampleForms.parse("""
				{"when": {"match": "any", "conditions": [{"question_id": "%s", "option": "%s"}]}, "go_to": %s}
				""".formatted(conditionQuestion, option, goTo));
	}

	private static void setRules(Map<String, Object> definition, int section, int question, Map<String, Object> rule) {
		SampleForms.question(definition, section, question).put("branch_rules", new ArrayList<>(List.of(rule)));
	}

	private static void setMode(Map<String, Object> definition, String mode) {
		SampleForms.settings(definition).put("display", Map.of("default_mode", mode));
	}

	@ParameterizedTest
	@ValueSource(strings = { "form_a_versions", "form_b_mixed", "form_summary", "form_required_branch" })
	void 시드의_모든_버전은_의미_검증을_통과한다(String fixture) throws Exception {
		JsonNode root = JSON.readTree(Path.of("data/seed/forms/" + fixture + ".json").toFile());
		for (JsonNode version : root.get("form_versions")) {
			Map<String, Object> definition = JSON.convertValue(version.get("form"), JSON.constructType(Map.class));
			assertThatCode(() -> FormPublishValidator.validate(definition)).doesNotThrowAnyException();
		}
	}

	@Test
	void 분기_없는_폼과_section_page_분기_폼은_통과한다() {
		assertThatCode(() -> FormPublishValidator.validate(SampleForms.unbranched())).doesNotThrowAnyException();
		assertThatCode(() -> FormPublishValidator.validate(SampleForms.branched())).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			// go_to가 없는 대상, 같은 위치, 앞쪽(AC8)
			"q2|예|{\"section_id\": \"none\"}", "q2|예|{\"question_id\": \"none\"}", "q2|예|{\"section_id\": \"s1\"}",
			"q2|예|{\"question_id\": \"q2\"}", "q2|예|{\"question_id\": \"q1\"}",
			// 조건이 없는 문항·선택지, 뒤 문항, 선택형이 아닌 문항(AC8)
			"none|예|{\"submit\": true}", "q2|없는 선택지|{\"submit\": true}", "q4|소감|{\"submit\": true}",
			"q3|역할|{\"submit\": true}" })
	void AC8_잘못된_분기는_invalid_branch다(String conditionQuestion, String option, String goTo) {
		Map<String, Object> definition = SampleForms.branched();
		setMode(definition, "one_question");
		setRules(definition, 0, 1, rule(conditionQuestion, option, goTo));

		assertRejected(definition, ErrorCode.INVALID_BRANCH);
	}

	@Test
	void AC46_one_question은_뒤쪽_문항과_제출로_분기할_수_있고_앞_문항을_조건으로_쓸_수_있다() {
		Map<String, Object> definition = SampleForms.branched();
		setMode(definition, "one_question");
		setRules(definition, 0, 0, rule("q1", "1학년", "{\"question_id\": \"q3\"}"));
		setRules(definition, 0, 1, rule("q1", "2학년", "{\"submit\": true}"));

		assertThatCode(() -> FormPublishValidator.validate(definition)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "section_page" })
	void AC46_분기가_없는_폼은_section_page를_쓸_수_없다(String mode) {
		Map<String, Object> definition = SampleForms.unbranched();
		setMode(definition, mode);

		assertRejected(definition, ErrorCode.INVALID_DISPLAY_MODE);
	}

	@Test
	void AC46_분기가_있는_폼은_all_questions를_쓸_수_없다() {
		Map<String, Object> definition = SampleForms.branched();
		setMode(definition, "all_questions");

		assertRejected(definition, ErrorCode.INVALID_DISPLAY_MODE);
	}

	@Test
	void AC46_section_page의_규칙은_섹션_마지막_문항에만_둔다() {
		Map<String, Object> definition = SampleForms.branched();
		setRules(definition, 0, 0, rule("q1", "1학년", "{\"section_id\": \"s3\"}"));

		assertRejected(definition, ErrorCode.INVALID_DISPLAY_MODE);
	}

	@Test
	void AC46_section_page는_문항으로_분기할_수_없다() {
		Map<String, Object> definition = SampleForms.branched();
		setRules(definition, 0, 1, rule("q2", "아니요", "{\"question_id\": \"q4\"}"));

		assertRejected(definition, ErrorCode.INVALID_DISPLAY_MODE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void F4_문항_문구가_비면_schema_invalid다(String note) {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.question(definition, 0, 0).put("question_note", note);

		assertRejected(definition, ErrorCode.SCHEMA_INVALID);
	}

	@Test
	void F4_선택형_문항에_선택지가_없으면_schema_invalid다() {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.question(definition, 0, 0).put("options", List.of());

		assertRejected(definition, ErrorCode.SCHEMA_INVALID);
	}

	@Test
	void 문항_ID가_중복되면_schema_invalid다() {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.question(definition, 1, 1).put("question_id", "q1");

		assertRejected(definition, ErrorCode.SCHEMA_INVALID);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 섹션_ID가_중복되면_schema_invalid다() {
		Map<String, Object> definition = SampleForms.unbranched();
		((Map<String, Object>) ((List<Object>) definition.get("sections")).get(1)).put("section_id", "s1");

		assertRejected(definition, ErrorCode.SCHEMA_INVALID);
	}

	@Test
	void 수집_항목은_형식_범주와_형식_없는_문항으로_만들고_중복하지_않는다() {
		assertThat(ConsentItems.of(SampleForms.unbranched())).containsExactly("문항별 응답 내용", "이름", "휴대전화 번호");
	}

	@Test
	void 수집_항목은_profile_key를_보지_않는다() {
		Map<String, Object> definition = SampleForms.unbranched();
		SampleForms.question(definition, 0, 0).put("profile_key", "department");

		assertThat(ConsentItems.of(definition)).containsExactly("문항별 응답 내용", "이름", "휴대전화 번호");
	}

	@Test
	void 운영_설정과_AI_요약은_버전_대상이_아니다() {
		Map<String, Object> changed = SampleForms.unbranched();
		changed.put("deadline", null);
		SampleForms.settings(changed).put("editable", true);
		SampleForms.settings(changed).put("cancellable", true);
		SampleForms.settings(changed).put("response_limit", 3);
		SampleForms.settings(changed).put("ai_summary", Map.of("enabled", true));

		assertThat(VersionTarget.same(SampleForms.unbranched(), changed)).isTrue();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "title|다른 제목", "note|다른 설명", "payment_link|https://pay.example.com/a",
			"consent_template_id|다른_템플릿" })
	void 응답자가_보는_값은_버전_대상이다(String key, String value) {
		Map<String, Object> changed = SampleForms.unbranched();
		changed.put(key, value);

		assertThat(VersionTarget.same(SampleForms.unbranched(), changed)).isFalse();
	}

	@Test
	void 보기_방식과_문항_순서는_버전_대상이다() {
		Map<String, Object> mode = SampleForms.unbranched();
		setMode(mode, "all_questions");
		Map<String, Object> reordered = SampleForms.unbranched();
		@SuppressWarnings("unchecked")
		List<Object> sections = (List<Object>) reordered.get("sections");
		sections.add(sections.remove(0));

		assertThat(VersionTarget.same(SampleForms.unbranched(), mode)).isFalse();
		assertThat(VersionTarget.same(SampleForms.unbranched(), reordered)).isFalse();
	}

}
