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

	@Test
	void 동작이_없으면_있어_끝말은_떼고_그대로_동작을_되묻는다() {
		RequestContext parsed = new RequestContext(List.of(), null, null, null, null, null);

		RequestContext revived = QueryText.reviveDroppedConditions(OPINION, parsed, VERSIONS);

		assertThat(revived.conditions()).doesNotContain("의견있어", "의견");
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

	@Test
	void target_요약에서도_원문의_선택지_문구는_되살린다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, "걱정되는 점", null);

		RequestContext revived = QueryText.reviveDroppedConditions("3학년 학생들의 걱정되는 점 요약해줘", parsed, VERSIONS);

		assertThat(revived.conditions()).containsExactly("3학년");
		assertThat(revived.target()).isEqualTo("걱정되는 점");
	}

}
