package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.form.VersionQuestions;

// #139: 조건이 빈 출력에서 sort 표현은 동작과 상관없이 되살리지 않는다(SPEC 318행: list가 아닌 sort는 검증하지 않고 무시).
// 정렬 문항 문구와 정렬 말("순으로", "낮은 순", "내림차순")만 빼고, 그 밖의 남는 낱말은 지금처럼 되살린다(SPEC 334행).
class SortPhraseRevivalTest {

	private static final List<VersionQuestions> VERSIONS = List.of(VersionQuestions.of(1,
			Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(
					Map.of("question_id", "q1", "question_note", "학년", "question_type", "multiple_choice", "options",
							List.of("1학년", "3학년")),
					Map.of("question_id", "q2", "question_note", "자기소개", "question_type", "long_answer", "options",
							List.of())))))));

	private static RequestContext parsed(String operation, String sortBy, String order) {
		return new RequestContext(List.of(), null, operation, new RequestContext.Sort(sortBy, order), null, null);
	}

	@Test
	void count에_붙은_sort_표현은_조건으로_되살리지_않는다() {
		RequestContext parsed = parsed("count", "자기소개", "asc");

		assertThat(QueryText.reviveDroppedConditions("전체 응답 수를 자기소개 순으로 알려줘", parsed, VERSIONS)).isSameAs(parsed);
	}

	@Test
	void summarize에_붙은_sort_표현은_조건으로_되살리지_않는다() {
		RequestContext parsed = parsed("summarize", "학년", "asc");

		assertThat(QueryText.reviveDroppedConditions("전체 응답을 학년 순서로 요약해줘", parsed, VERSIONS)).isSameAs(parsed);
	}

	@Test
	void list에서_띄어_쓴_정렬_말도_조건으로_되살리지_않는다() {
		RequestContext parsed = parsed("list", "자기소개", "asc");

		assertThat(QueryText.reviveDroppedConditions("전체 응답을 자기소개 순으로 보여줘", parsed, VERSIONS)).isSameAs(parsed);
	}

	@Test
	void 방향_말과_기준_말도_조건으로_되살리지_않는다() {
		assertThat(QueryText.reviveDroppedConditions("학년 낮은 순으로 보여줘", parsed("list", "학년", "asc"), VERSIONS)
			.conditions()).isEmpty();
		assertThat(QueryText.reviveDroppedConditions("학년 기준 내림차순으로 보여줘", parsed("list", "학년", "desc"), VERSIONS)
			.conditions()).isEmpty();
	}

	@Test
	void sort가_있어도_그_밖의_남는_낱말은_되살린다() {
		RequestContext parsed = parsed("count", "학년", "asc");

		assertThat(QueryText.reviveDroppedConditions("남학생 수를 학년순으로 알려줘", parsed, VERSIONS).conditions())
			.containsExactly("남학생");
	}

	@Test
	void sort가_없으면_정렬_말도_지금처럼_되살린다() {
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		assertThat(QueryText.reviveDroppedConditions("자기소개 순으로 몇 명", parsed, VERSIONS).conditions()).isNotEmpty();
	}

}
