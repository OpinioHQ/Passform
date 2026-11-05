package com.opinio.passform.domain.form;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.question;
import static com.opinio.passform.support.FormDefinitions.rule;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static com.opinio.passform.support.FormDefinitions.toQuestion;
import static com.opinio.passform.support.FormDefinitions.toSection;
import static com.opinio.passform.support.FormDefinitions.toSubmit;
import static com.opinio.passform.support.FormDefinitions.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.form.ResponsePathCalculator.ResponsePath;

class ResponsePathCalculatorTest {

	private static final List<String> YES_NO = List.of("예", "아니오");

	private static final List<String> GRADES = List.of("3학년", "4학년");

	private static ResponsePath path(Map<String, Object> definition, Map<String, List<String>> answers) {
		return ResponsePathCalculator.calculate(FormDefinition.from(definition), answers);
	}

	private static List<String> ids(List<Question> questions) {
		return questions.stream().map(Question::questionId).toList();
	}

	@Test
	void AC9_아니오면_s3로_건너뛰고_s2_문항은_경로_밖이다() {
		Map<String, Object> form = definition("section_page", List.of(
				section("s1", question("q1", "multiple_choice", true, YES_NO),
						question("q2", "multiple_choice", true, YES_NO, rule("all", toSection("s3"), when("q2", "아니오")))),
				section("s2", text("q3", true)),
				section("s3", text("q4", true))));

		ResponsePath path = path(form, Map.of("q1", List.of("예"), "q2", List.of("아니오")));

		assertThat(ids(path.onPath())).containsExactly("q1", "q2", "q4");
		assertThat(ids(path.skipped())).containsExactly("q3");
	}

	private static Map<String, Object> combination(String match) {
		return definition("section_page", List.of(
				section("s1", question("q2", "multiple_choice", true, YES_NO),
						question("q3", "multiple_choice", true, GRADES,
								rule(match, toSection("s4"), when("q2", "예"), when("q3", "4학년")))),
				section("s2", text("q4", false)),
				section("s4", text("q5", false))));
	}

	@Test
	void AC44_all은_조합이_모두_맞아야_이동한다() {
		assertThat(ids(path(combination("all"), Map.of("q2", List.of("예"), "q3", List.of("4학년"))).onPath()))
			.containsExactly("q2", "q3", "q5");
		assertThat(ids(path(combination("all"), Map.of("q2", List.of("예"), "q3", List.of("3학년"))).onPath()))
			.containsExactly("q2", "q3", "q4", "q5");
	}

	@Test
	void AC44_any는_하나만_맞아도_이동한다() {
		assertThat(ids(path(combination("any"), Map.of("q2", List.of("아니오"), "q3", List.of("4학년"))).onPath()))
			.containsExactly("q2", "q3", "q5");
	}

	@Test
	void AC44_여러_규칙이_맞으면_위의_규칙을_따른다() {
		Map<String, Object> form = definition("one_question", List.of(section("s1",
				question("q1", "multiple_choice", true, YES_NO, rule("all", toQuestion("q3"), when("q1", "예")),
						rule("all", toQuestion("q4"), when("q1", "예"))),
				text("q2", false), text("q3", false), text("q4", false))));

		assertThat(ids(path(form, Map.of("q1", List.of("예"))).onPath())).containsExactly("q1", "q3", "q4");
	}

	@Test
	void checkbox_조건은_그_선택지를_포함하면_맞다() {
		Map<String, Object> form = definition("one_question", List.of(section("s1",
				question("q1", "checkbox", false, List.of("가", "나"), rule("all", toQuestion("q3"), when("q1", "나"))),
				text("q2", false), text("q3", false))));

		assertThat(ids(path(form, Map.of("q1", List.of("가", "나"))).onPath())).containsExactly("q1", "q3");
		assertThat(ids(path(form, Map.of("q1", List.of("가"))).onPath())).containsExactly("q1", "q2", "q3");
	}

	@Test
	void one_question은_규칙이_없으면_다음_문항이고_제출_이동이면_끝난다() {
		Map<String, Object> form = definition("one_question", List.of(
				section("s1", question("q1", "multiple_choice", true, YES_NO, rule("all", toSubmit(), when("q1", "아니오"))),
						text("q2", false)),
				section("s2", text("q3", false))));

		assertThat(ids(path(form, Map.of("q1", List.of("예"))).onPath())).containsExactly("q1", "q2", "q3");
		ResponsePath submitted = path(form, Map.of("q1", List.of("아니오")));
		assertThat(ids(submitted.onPath())).containsExactly("q1");
		assertThat(ids(submitted.skipped())).containsExactly("q2", "q3");
	}

	@Test
	void section_page는_섹션을_한꺼번에_지나고_맞는_규칙이_없으면_다음_섹션이다() {
		Map<String, Object> form = definition("section_page", List.of(
				section("s1", question("q1", "multiple_choice", true, YES_NO), text("q2", false)),
				section("s2", text("q3", false))));

		assertThat(ids(path(form, Map.of()).onPath())).containsExactly("q1", "q2", "q3");
	}

	@Test
	void 답이_없어도_경로를_계산한다() {
		assertThat(ids(path(combination("all"), Map.of()).onPath())).containsExactly("q2", "q3", "q4", "q5");
	}

	@Test
	void 건너뛴_문항에_보낸_답은_뒤의_분기에_영향을_주지_않는다() {
		Map<String, Object> form = definition("one_question", List.of(section("s1",
				question("q1", "multiple_choice", true, YES_NO, rule("all", toQuestion("q3"), when("q1", "아니오"))),
				question("q2", "multiple_choice", false, YES_NO),
				question("q3", "multiple_choice", false, YES_NO, rule("all", toSubmit(), when("q2", "예"))),
				text("q4", false))));

		assertThat(ids(path(form, Map.of("q1", List.of("아니오"), "q2", List.of("예"), "q3", List.of("예"))).onPath()))
			.containsExactly("q1", "q3", "q4");
	}

	@Test
	void 뒤로_가거나_없는_대상이어도_반복에_빠지지_않는다() {
		Map<String, Object> form = definition("one_question", List.of(section("s1", text("q1", false),
				question("q2", "multiple_choice", false, YES_NO, rule("all", toQuestion("q1"), when("q2", "예"))),
				question("q3", "multiple_choice", false, YES_NO, rule("all", toSection("none"), when("q3", "예"))),
				text("q4", false))));

		ResponsePath path = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> path(form, Map.of("q2", List.of("예"), "q3", List.of("예"))));

		assertThat(ids(path.onPath())).containsExactly("q1", "q2", "q3", "q4");
	}

	@Test
	void 대상_섹션이_비어_있으면_그다음_문항으로_간다() {
		Map<String, Object> form = definition("section_page", List.of(
				section("s1", question("q1", "multiple_choice", true, YES_NO, rule("all", toSection("s3"), when("q1", "예")))),
				section("s2", text("q2", false)),
				section("s3"),
				section("s4", text("q3", false))));

		assertThat(ids(path(form, Map.of("q1", List.of("예"))).onPath())).containsExactly("q1", "q3");
	}

}
