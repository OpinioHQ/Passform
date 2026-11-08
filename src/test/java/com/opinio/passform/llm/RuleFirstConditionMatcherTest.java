package com.opinio.passform.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.form.VersionQuestions;

// 규칙 매처: 글자가 같은 조건만 풀고 못 푼 (조건, 버전)만 다음 매처에 보낸다(analyze_api.md 결정 A9).
class RuleFirstConditionMatcherTest {

	private final ConditionMatcher next = mock(ConditionMatcher.class);

	private final RuleFirstConditionMatcher matcher = new RuleFirstConditionMatcher(next);

	private static VersionQuestions version(int formVersion, List<String> departments) {
		return VersionQuestions.of(formVersion, Map.of("sections", List.of(Map.of("section_id", "s1", "questions",
				List.of(Map.of("question_id", "q1", "question_note", "학과", "question_type", "dropdown", "options", departments),
						Map.of("question_id", "q3", "question_note", "자기소개", "question_type", "long_answer", "options",
								List.of()))))));
	}

	@Test
	void 선택지와_글자가_같으면_option이고_다음_매처를_부르지_않는다() {
		List<ConditionMatch> matches = matcher.match(List.of("경영학과"), List.of(version(1, List.of("소프트웨어학과", "경영학과"))));

		assertThat(matches).containsExactly(new ConditionMatch("경영학과", 1, "q1", MatchKind.OPTION, "경영학과"));
		verify(next, never()).match(anyList(), anyList());
	}

	@Test
	void 문항_문구와_미응답이면_unanswered다() {
		List<ConditionMatch> matches = matcher.match(List.of("자기소개 - 미응답", "자기소개 - 응답"),
				List.of(version(1, List.of("경영학과"))));

		assertThat(matches).containsExactly(new ConditionMatch("자기소개 - 미응답", 1, "q3", MatchKind.UNANSWERED, null),
				new ConditionMatch("자기소개 - 응답", 1, "q3", MatchKind.ANSWERED, null));
		verify(next, never()).match(anyList(), anyList());
	}

	@Test
	void 버전마다_그_버전의_선택지로만_푼다() {
		VersionQuestions v1 = version(1, List.of("컴퓨터공학과"));
		VersionQuestions v2 = version(2, List.of("컴퓨터공학부"));
		when(next.match(List.of("컴퓨터공학과"), List.of(v2))).thenReturn(List.of());

		List<ConditionMatch> matches = matcher.match(List.of("컴퓨터공학과"), List.of(v1, v2));

		assertThat(matches).containsExactly(new ConditionMatch("컴퓨터공학과", 1, "q1", MatchKind.OPTION, "컴퓨터공학과"));
		verify(next).match(List.of("컴퓨터공학과"), List.of(v2));
	}

	@Test
	void 못_푼_조건만_보내고_보내지_않은_값과_허용_밖_값은_버린다() {
		VersionQuestions v1 = version(1, List.of("소프트웨어학과", "경영학과"));
		ConditionMatch alias = new ConditionMatch("소웨", 1, "q1", MatchKind.OPTION, "소프트웨어학과");
		when(next.match(List.of("소웨"), List.of(v1))).thenReturn(List.of(alias,
				new ConditionMatch("경영학과", 1, "q1", MatchKind.OPTION, "경영학과"),
				new ConditionMatch("소웨", 1, "q1", MatchKind.OPTION, "없는 선택지"),
				new ConditionMatch("소웨", 1, "q9", MatchKind.ANSWERED, null)));

		List<ConditionMatch> matches = matcher.match(List.of("경영학과", "소웨"), List.of(v1));

		assertThat(matches).containsExactly(new ConditionMatch("경영학과", 1, "q1", MatchKind.OPTION, "경영학과"), alias);
		verify(next).match(List.of("소웨"), List.of(v1));
	}

}
