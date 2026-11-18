package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.form.VersionQuestions;

// #139: 조건이 빈 출력에서 정렬 표현은 조건으로 되살리지 않는다(SPEC 318행). 그 밖의 남는 낱말은 지금처럼 되살린다(SPEC 341행).
// 정렬 표현은 낱말 모양으로 본다: 정렬 기준 낱말 그 자체이거나 그 뒤에 정렬 말·조사만 붙은 꼴("학년순으로", "나이가"),
// 정렬 말("순으로", "기준"), 그리고 "순" 앞의 방향 말("낮은 순", "낮은순"). 정렬 기준을 품은 다른 낱말("3학년")은 조건이다.
// list가 아닌 sort는 sort.by가 문항 문구이고, 선택지가 아니며, 원문에 정렬 표시가 있을 때만 정렬 표현으로 본다.
// 그렇지 않으면 파서가 조건을 sort에 잘못 담았을 수 있으므로 sort를 없는 것으로 보고 되살린다(#44).
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

	// --- PR #142 리뷰 1: 정렬 기준 낱말을 품은 조건은 덮지 않는다 ---

	@Test
	void count에서_정렬_기준을_품은_선택지는_조건으로_되살린다() {
		RequestContext revived = QueryText.reviveDroppedConditions("3학년 학생 수를 학년순으로 알려줘",
				parsed("count", "학년", "asc"), VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년");
	}

	@Test
	void 여러_낱말_정렬_기준도_그_낱말을_품은_선택지는_되살린다() {
		RequestContext revived = QueryText.reviveDroppedConditions("소프트웨어학과 수를 소속 학과 순으로 알려줘",
				parsed("count", "소속 학과", "asc"), DEPARTMENT);

		assertThat(revived.conditions()).containsExactly("소프트웨어학과");
	}

	@Test
	void list에서_띄어_쓴_정렬_말과_함께_온_조건도_되살린다() {
		RequestContext revived = QueryText.reviveDroppedConditions("3학년 응답을 학년 순으로 보여줘",
				parsed("list", "학년", "asc"), VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년");
	}

	// --- PR #142 리뷰 2: 방향 말은 "순" 앞에 있을 때만 정렬 말이다 ---

	@Test
	void 순_앞이_아닌_방향_말은_조건으로_되살린다() {
		assertThat(QueryText.reviveDroppedConditions("어린 사람 수를 학년순으로 알려줘", parsed("count", "학년", "asc"), VERSIONS)
			.conditions()).containsExactly("어린");
		assertThat(QueryText.reviveDroppedConditions("어린 사람을 학년순으로 보여줘", parsed("list", "학년", "asc"), VERSIONS)
			.conditions()).containsExactly("어린");
	}

	@Test
	void 순위처럼_순으로_시작하는_다른_낱말_앞의_방향_말은_되살린다() {
		RequestContext revived = QueryText.reviveDroppedConditions("높은 순위 지원자 수를 학년순으로 알려줘",
				parsed("count", "학년", "asc"), VERSIONS);

		assertThat(revived.conditions()).containsExactly("높은", "순위");
	}

	// --- PR #142 리뷰 3: sort.by와 같은 글자의 선택지는 가리지 않는다 ---

	@Test
	void 조건이_있을_때_sort_by와_같은_선택지는_되살린다() {
		RequestContext parsed = new RequestContext(List.of("3학년"), null, "count", new RequestContext.Sort("기획", "asc"),
				null, null);

		assertThat(QueryText.reviveDroppedOptions("기획 3학년 수 알려줘", parsed, PLANNING).conditions())
			.containsExactly("3학년", "기획");
	}

	@Test
	void 조건이_없을_때도_sort_by와_같은_선택지는_되살린다() {
		RequestContext revived = QueryText.reviveDroppedConditions("기획 학생 수를 기획순으로 알려줘",
				parsed("count", "기획", "asc"), PLANNING);

		assertThat(revived.conditions()).contains("기획");
	}

	// --- 정렬 표시가 없으면 문항 이름도 조건일 수 있다(list가 아닐 때) ---

	@Test
	void count에서_정렬_표시_없이_문항_이름만_있으면_되살린다() {
		RequestContext revived = QueryText.reviveDroppedConditions("학년 몇 명이야?", parsed("count", "학년", "asc"), VERSIONS);

		assertThat(revived.conditions()).containsExactly("학년");
	}

	// --- 지금 되는 정렬 표현은 그대로 정렬 표현이다 ---

	@Test
	void list의_붙여_쓴_정렬_꼴은_되살리지_않는다() {
		for (String query : List.of("학년오름차순으로 보여줘", "학년순서대로 보여줘", "학년기준으로 보여줘", "학년 많은순으로 보여줘",
				"학년을 기준으로 보여줘")) {
			assertThat(QueryText.reviveDroppedConditions(query, parsed("list", "학년", "asc"), VERSIONS).conditions())
				.as(query)
				.isEmpty();
		}
	}

	@Test
	void 여러_낱말_정렬_기준을_붙여_써도_되살리지_않는다() {
		assertThat(QueryText.reviveDroppedConditions("소속학과순으로 보여줘", parsed("list", "소속 학과", "asc"), DEPARTMENT)
			.conditions()).isEmpty();
	}

	@Test
	void 정렬_기준에_조사가_붙고_방향_말이_와도_되살리지_않는다() {
		assertThat(QueryText.reviveDroppedConditions("나이가 많은 순으로 보여줘", parsed("list", "나이", "desc"), AGE)
			.conditions()).isEmpty();
		assertThat(QueryText.reviveDroppedConditions("나이순으로 보여줘", parsed("list", "나이", "asc"), AGE).conditions())
			.isEmpty();
	}

	// --- 혼합 부정 검사도 같은 정렬 표현 규칙을 쓴다 ---

	@Test
	void list에서_정렬_기준을_품은_긍정_조건과_부정이_섞이면_혼합_부정이다() {
		RequestContext context = new RequestContext(List.of("1학년"), "not", "list", new RequestContext.Sort("학년", "asc"),
				null, null);

		assertThat(QueryText.mixesNegation("1학년 제외하고 3학년 응답을 학년순으로 보여줘", context, VERSIONS)).isTrue();
		assertThat(QueryText.mixesNegation("1학년 제외하고 학년순으로 보여줘", context, VERSIONS)).isFalse();
	}

	private static final List<VersionQuestions> DEPARTMENT = List.of(VersionQuestions.of(1,
			Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(Map.of("question_id", "q1",
					"question_note", "소속 학과", "question_type", "dropdown", "options", List.of("소프트웨어학과", "경영학과"))))))));

	// 문항 문구 "기획"이 다른 문항의 선택지 "기획"과 같은 폼(제작자가 만들 수 있다).
	private static final List<VersionQuestions> PLANNING = List.of(VersionQuestions.of(1,
			Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(
					Map.of("question_id", "q1", "question_note", "학년", "question_type", "multiple_choice", "options",
							List.of("1학년", "3학년")),
					Map.of("question_id", "q2", "question_note", "지원 분야", "question_type", "multiple_choice", "options",
							List.of("기획", "운영")),
					Map.of("question_id", "q3", "question_note", "기획", "question_type", "long_answer", "options",
							List.of())))))));

	private static final List<VersionQuestions> AGE = List.of(VersionQuestions.of(1,
			Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(Map.of("question_id", "q1",
					"question_note", "나이", "question_type", "short_answer", "options", List.of())))))));

}
