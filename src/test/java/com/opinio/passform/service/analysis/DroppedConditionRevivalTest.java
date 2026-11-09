package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.form.VersionQuestions;

// SPEC 334행 파서 출력 보정: 조건이 빈 출력에서 남는 낱말을 되살리는 범위.
// target·topic이 있는 요약은 선택지 문구만, count·list·동작 없음은 지금처럼 낱말을 되살린다.
class DroppedConditionRevivalTest {

	private static final List<VersionQuestions> VERSIONS = List.of(VersionQuestions.of(1,
			Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(
					Map.of("question_id", "q1", "question_note", "학년", "question_type", "multiple_choice", "options",
							List.of("1학년", "3학년")),
					Map.of("question_id", "q2", "question_note", "걱정되는 점", "question_type", "long_answer", "options",
							List.of()),
					Map.of("question_id", "q3", "question_note", "하고 싶은 말", "question_type", "long_answer", "options",
							List.of())))))));

	private static final String OPINION = "비용이 부담된다는 의견있어?";

	@Test
	void topic_요약에서_남은_낱말은_조건으로_되살리지_않는다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, null, "비용이 부담된다는");

		RequestContext revived = QueryText.reviveDroppedConditions(OPINION, parsed, VERSIONS);

		assertThat(revived).isSameAs(parsed);
		assertThat(ClarifyRules.beforeMatching(OPINION, revived, true, false, VERSIONS)).isEmpty();
	}

	// 팀원 리뷰 반영(사람 승인, 2026-11-09): 동작이 없으면 "있어" 끝말을 떼지 않고 되살린다. 동작을 고른 뒤 조건 빠진 숫자가 나가지 않게.
	@Test
	void 동작이_없으면_있어_끝말을_떼지_않고_되살려_동작을_되묻는다() {
		RequestContext parsed = new RequestContext(List.of(), null, null, null, null, null);

		RequestContext revived = QueryText.reviveDroppedConditions(OPINION, parsed, VERSIONS);

		assertThat(revived.conditions()).contains("의견있어").doesNotContain("의견");
		assertThat(ClarifyRules.beforeMatching(OPINION, revived, true, false, VERSIONS))
			.hasValueSatisfying(clarify -> assertThat(clarify.reason()).isEqualTo("missing_operation"));
	}

	@Test
	void count에서_조건을_비운_출력은_지금처럼_되살려_되묻는다() {
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		RequestContext revived = QueryText.reviveDroppedConditions("남학생 몇 명", parsed, VERSIONS);

		assertThat(revived.conditions()).containsExactly("남학생");
		assertThat(ClarifyRules.beforeMatching("남학생 몇 명", revived, true, false, VERSIONS)).isEmpty();
		assertThat(ClarifyRules.afterMatching(revived, List.of(), VERSIONS, null))
			.hasValueSatisfying(clarify -> assertThat(clarify.reason()).isEqualTo("unknown_condition"));
	}

	// 코덱스 검토 반영 1: 끝말을 떼면 빈 낱말이 되는 "있어"는 그대로 둔다(선택지일 수 있다).
	@Test
	void 낱말_자체가_있어면_떼지_않고_되살린다() {
		List<VersionQuestions> versions = List.of(VersionQuestions.of(1, Map.of("sections", List.of(Map.of("section_id",
				"s1", "questions", List.of(Map.of("question_id", "q1", "question_note", "자차 여부", "question_type",
						"multiple_choice", "options", List.of("있어", "없어"))))))));
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		assertThat(QueryText.reviveDroppedConditions("있어 몇 명?", parsed, versions).conditions()).containsExactly("있어");
	}

	// 코덱스 검토 반영 2: 부정이 있으면 선택지만 되살리지 않고 예전처럼 한 덩어리로 되살려 되묻는다.
	@Test
	void 요약이어도_부정이_있으면_선택지만_되살리지_않는다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, "걱정되는 점", null);

		RequestContext revived = QueryText.reviveDroppedConditions("3학년 아닌 학생들의 걱정되는 점 요약해줘", parsed, VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년 아닌");
	}

	// 코덱스 검토 반영 2: 선택지로 설명되지 않는 조건 낱말("3학년생")이 남으면 전체 요약으로 가지 않고 예전처럼 되살린다.
	@Test
	void 요약이어도_선택지로_설명되지_않는_낱말이_남으면_예전처럼_되살린다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, "걱정되는 점", null);

		RequestContext revived = QueryText.reviveDroppedConditions("3학년생 걱정되는 점 요약해줘", parsed, VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년생");
		assertThat(ClarifyRules.afterMatching(revived, List.of(), VERSIONS, null))
			.hasValueSatisfying(clarify -> assertThat(clarify.reason()).isEqualTo("unknown_condition"));
	}

	@Test
	void target_요약에서도_원문의_선택지_문구는_되살린다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, "걱정되는 점", null);

		RequestContext revived = QueryText.reviveDroppedConditions("3학년 학생들의 걱정되는 점 요약해줘", parsed, VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년");
		assertThat(revived.target()).isEqualTo("걱정되는 점");
	}

	private static List<VersionQuestions> choiceForm(String note, String... options) {
		return List.of(VersionQuestions.of(1, Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(
				Map.of("question_id", "q1", "question_note", note, "question_type", "multiple_choice", "options",
						List.of(options)),
				Map.of("question_id", "q2", "question_note", "걱정되는 점", "question_type", "long_answer", "options",
						List.of())))))));
	}

	// 팀원 리뷰 반영 (가): count에서는 "있어" 끝말을 떼지 않는다. 전체 건수로 가지 않고 되묻는다.
	@Test
	void count에서는_의견있어를_떼지_않고_되살려_되묻는다() {
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		RequestContext revived = QueryText.reviveDroppedConditions("의견있어 몇 명?", parsed, VERSIONS);

		assertThat(revived.conditions()).containsExactly("의견있어");
		assertThat(ClarifyRules.afterMatching(revived, List.of(), VERSIONS, null))
			.hasValueSatisfying(clarify -> assertThat(clarify.reason()).isEqualTo("unknown_condition"));
	}

	// 팀원 리뷰 반영 (나): "있어요"로 끝나는 선택지 문구가 잘리지 않는다.
	@Test
	void count에서_있어요로_끝나는_선택지는_그대로_되살린다() {
		List<VersionQuestions> versions = choiceForm("관심 여부", "관심있어요", "관심없어요");
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		assertThat(QueryText.reviveDroppedConditions("관심있어요 몇 명?", parsed, versions).conditions())
			.containsExactly("관심있어요");
	}

	// 팀원 리뷰 반영 (다): 요약에서 띄어 쓴 "있어"는 조건으로 되살리지 않는다.
	@Test
	void 요약에서_띄어_쓴_있어는_조건으로_되살리지_않는다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, null, "비용이 부담된다는");

		RequestContext revived = QueryText.reviveDroppedConditions("비용이 부담된다는 의견 있어?", parsed, VERSIONS);

		assertThat(revived.conditions()).isEmpty();
	}

	// 팀원 리뷰 반영 (라): 요약이어도 이 폼의 선택지 "있어"는 조건으로 되살린다.
	@Test
	void 요약에서_선택지와_같은_있어는_되살린다() {
		List<VersionQuestions> versions = choiceForm("자차 여부", "있어", "없어");
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, "걱정되는 점", null);

		assertThat(QueryText.reviveDroppedConditions("있어 학생들의 걱정되는 점 요약해줘", parsed, versions).conditions())
			.containsExactly("있어");
	}

}
