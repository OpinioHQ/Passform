package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.form.VersionQuestions;

// SPEC 334행 파서 출력 보정: 버린 선택지 문구만 원문에서 되살린다.
class DroppedOptionRevivalTest {

	private static final List<VersionQuestions> VERSIONS = List.of(VersionQuestions.of(1,
			Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(
					Map.of("question_id", "q1", "question_note", "학과", "question_type", "dropdown", "options",
							List.of("소프트웨어학과", "경영학과")),
					Map.of("question_id", "q2", "question_note", "학년", "question_type", "multiple_choice", "options",
							List.of("1학년", "3학년")),
					Map.of("question_id", "q5", "question_note", "참석 여부", "question_type", "multiple_choice", "options",
							List.of("예", "아니요"))))))));

	private static RequestContext parsed(String... conditions) {
		return new RequestContext(List.of(conditions), null, "count", null, null, null);
	}

	@SafeVarargs
	private static List<VersionQuestions> form(Map<String, Object>... questions) {
		return List.of(VersionQuestions.of(1,
				Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(questions))))));
	}

	private static Map<String, Object> choice(String id, String note, String... options) {
		return Map.of("question_id", id, "question_note", note, "question_type", "multiple_choice", "options",
				List.of(options));
	}

	@Test
	void 문항_문구에_든_부정어도_부정으로_세어_혼합_부정을_되묻는다() {
		List<VersionQuestions> versions = form(choice("q1", "학과", "경영학과", "소프트웨어학과"),
				choice("q2", "학년", "1학년", "3학년"), Map.of("question_id", "q3", "question_note", "제외 사유", "question_type",
						"short_answer", "options", List.of()));
		RequestContext context = new RequestContext(List.of("경영학과", "3학년"), "not", "count", null, null, null);

		assertThat(ClarifyRules.beforeMatching("경영학과 제외 3학년 수", context, false, false, versions))
			.hasValueSatisfying(clarify -> assertThat(clarify.reason()).isEqualTo("missing_match"));
	}

	@Test
	void 이미_든_조건이_나온_자리_밖의_같은_글자_선택지는_되살린다() {
		List<VersionQuestions> versions = form(choice("q1", "부서", "기획팀", "개발팀"), choice("q2", "직무", "기획", "개발"));

		RequestContext revived = QueryText.reviveDroppedOptions("기획팀 기획인 사람 수", parsed("기획팀"), versions);

		assertThat(revived.conditions()).containsExactly("기획팀", "기획");
		assertThat(ClarifyRules.beforeMatching("기획팀 기획인 사람 수", revived, false, false, versions))
			.hasValueSatisfying(clarify -> assertThat(clarify.reason()).isEqualTo("missing_match"));
	}

	@Test
	void 쉼표_앞의_선택지_문구도_낱말_끝으로_보고_되살린다() {
		RequestContext revived = QueryText.reviveDroppedOptions("소프트웨어학과,3학년 학생 수", parsed("3학년"), VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년", "소프트웨어학과");
	}

	@Test
	void 일반어와_같은_선택지는_되살리지_않는다() {
		List<VersionQuestions> versions = form(choice("q1", "학과", "경영학과", "소프트웨어학과"),
				choice("q2", "요일", "월", "화", "수", "목", "금"));
		RequestContext context = parsed("경영학과");

		RequestContext revived = QueryText.reviveDroppedOptions("경영학과 학생 수 알려줘", context, versions);

		assertThat(revived).isSameAs(context);
		assertThat(ClarifyRules.beforeMatching("경영학과 학생 수 알려줘", revived, false, false, versions)).isEmpty();
	}

	@Test
	void 원문에_있는데_조건에_없는_선택지_문구를_되살리고_match는_그대로다() {
		RequestContext revived = QueryText.reviveDroppedOptions("소프트웨어학과 3학년 학생 수 알려줘", parsed("소프트웨어학과"), VERSIONS);

		assertThat(revived.conditions()).containsExactly("소프트웨어학과", "3학년");
		assertThat(revived.match()).isNull();
	}

	@Test
	void 이미_든_조건의_일부_글자와_낱말_안의_글자는_되살리지_않는다() {
		RequestContext inCondition = parsed("소프트웨어학과 3학년");
		RequestContext inWord = parsed("경영학과");

		assertThat(QueryText.reviveDroppedOptions("소프트웨어학과 3학년 몇 명", inCondition, VERSIONS)).isSameAs(inCondition);
		assertThat(QueryText.reviveDroppedOptions("경영학과 중 예정된 학생 수", inWord, VERSIONS)).isSameAs(inWord);
	}

}
