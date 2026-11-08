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
