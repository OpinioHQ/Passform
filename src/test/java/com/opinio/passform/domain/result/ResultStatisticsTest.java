package com.opinio.passform.domain.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.result.FormResults.CombinedOption;
import com.opinio.passform.domain.result.FormResults.CombinedQuestion;

// AC19 테스트 줄(SPEC 503행)과 사람이 정한 통계 규칙. DB 없이 계산만 본다.
class ResultStatisticsTest {

	private static final DefinitionQuestion V1_Q1 = choice("q1", "참석하나요?", "multiple_choice", "예", "아니요");

	private static final DefinitionQuestion V2_Q1 = choice("q1", "참석하나요?", "multiple_choice", "예", "아니요", "보류");

	@Test
	void AC19_같은_문항의_같은_선택지만_합치고_보류는_버전_2만_출처로_붙는다() {
		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(V1_Q1), 2, List.of(V2_Q1)),
				List.of(answer("a", 1, "q1", "예"), answer("b", 1, "q1", "아니요"), answer("c", 1, "q1", "예"),
						answer("d", 2, "q1", "예"), answer("e", 2, "q1", "보류"), answer("f", 2, "q1", "아니요"),
						answer("g", 2, "q1", "아니요")));

		assertThat(results.combined().questions()).containsExactly(new CombinedQuestion("q1", "참석하나요?",
				"multiple_choice", List.of(1, 2), List.of(new CombinedOption("예", 3, List.of(1, 2)),
						new CombinedOption("아니요", 3, List.of(1, 2)), new CombinedOption("보류", 1, List.of(2)))));
	}

	@Test
	void AC19_버전별_통계는_각_버전_응답만_세고_전체_수는_두_버전의_합이다() {
		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(V1_Q1), 2, List.of(V2_Q1)),
				List.of(answer("a", 1, "q1", "예"), answer("b", 1, "q1", "아니요"), answer("c", 2, "q1", "보류")));

		assertThat(results.responseCount()).isEqualTo(3);
		assertThat(results.versions()).extracting(FormResults.Version::formVersion, FormResults.Version::responseCount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(1, 2), org.assertj.core.groups.Tuple.tuple(2, 1));
		assertThat(results.versions().get(0).questions().get(0).counts())
			.containsExactlyEntriesOf(linked("예", 1, "아니요", 1));
		assertThat(results.versions().get(1).questions().get(0).counts())
			.containsExactlyEntriesOf(linked("예", 0, "아니요", 0, "보류", 1));
	}

	@Test
	void AC19_버전_2의_문구가_달라지면_통합에서_별도_문항이다() {
		DefinitionQuestion renamed = choice("q1", "참석할 예정인가요?", "multiple_choice", "예", "아니요", "보류");

		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(V1_Q1), 2, List.of(renamed)),
				List.of(answer("a", 1, "q1", "예"), answer("b", 2, "q1", "예")));

		assertThat(results.combined().questions()).extracting(CombinedQuestion::questionNote,
				CombinedQuestion::formVersions)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("참석하나요?", List.of(1)),
					org.assertj.core.groups.Tuple.tuple("참석할 예정인가요?", List.of(2)));
		assertThat(results.combined().questions().get(0).options().get(0)).isEqualTo(new CombinedOption("예", 1, List.of(1)));
		assertThat(results.combined().questions().get(1).options().get(0)).isEqualTo(new CombinedOption("예", 1, List.of(2)));
	}

	@Test
	void AC19_버전_2의_유형이_달라지면_통합에서_별도_문항이다() {
		DefinitionQuestion retyped = choice("q1", "참석하나요?", "dropdown", "예", "아니요", "보류");

		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(V1_Q1), 2, List.of(retyped)),
				List.of(answer("a", 1, "q1", "예"), answer("b", 2, "q1", "예")));

		assertThat(results.combined().questions()).extracting(CombinedQuestion::questionType,
				CombinedQuestion::formVersions)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("multiple_choice", List.of(1)),
					org.assertj.core.groups.Tuple.tuple("dropdown", List.of(2)));
	}

	@Test
	void 같은_위치라도_문구가_다른_선택지는_합치지_않는다() {
		DefinitionQuestion v1 = choice("q1", "학과", "dropdown", "가학과", "나학과");
		DefinitionQuestion v2 = choice("q1", "학과", "dropdown", "가학과", "다학과");

		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(v1), 2, List.of(v2)),
				List.of(answer("a", 1, "q1", "나학과"), answer("b", 2, "q1", "다학과")));

		assertThat(results.combined().questions().get(0).options()).containsExactly(
				new CombinedOption("가학과", 0, List.of(1, 2)), new CombinedOption("나학과", 1, List.of(1)),
				new CombinedOption("다학과", 1, List.of(2)));
	}

	@Test
	void checkbox는_고른_선택지마다_한_번씩_세고_같은_값이_겹쳐도_한_번이다() {
		DefinitionQuestion checkbox = choice("q3", "관심 분야", "checkbox", "기획", "디자인");

		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(checkbox)),
				List.of(answer("a", 1, "q3", "기획", "디자인"), answer("b", 1, "q3", "기획", "기획")));

		assertThat(results.versions().get(0).questions().get(0).counts())
			.containsExactlyEntriesOf(linked("기획", 2, "디자인", 1));
	}

	@Test
	void 주관식은_넣지_않고_정의에_없는_답은_세지_않는다() {
		DefinitionQuestion longAnswer = new DefinitionQuestion("q4", "자기소개", "long_answer", List.of());

		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(V1_Q1, longAnswer)),
				List.of(answer("a", 1, "q1", "모름"), answer("b", 1, "q4", "안녕하세요"), answer("c", 1, "q9", "예")));

		assertThat(results.responseCount()).isEqualTo(3);
		assertThat(results.combined().questions()).extracting(CombinedQuestion::questionId).containsExactly("q1");
		assertThat(results.versions().get(0).questions()).hasSize(1);
		assertThat(results.versions().get(0).questions().get(0).counts())
			.containsExactlyEntriesOf(linked("예", 0, "아니요", 0));
	}

	@Test
	void 응답이_없으면_빈_결과다() {
		FormResults results = ResultStatistics.calculate(Map.of(), List.of());

		assertThat(results.responseCount()).isZero();
		assertThat(results.combined().questions()).isEmpty();
		assertThat(results.versions()).isEmpty();
	}

	@Test
	void 응답이_있는_버전의_정의가_없으면_실패한다() {
		assertThatThrownBy(() -> ResultStatistics.calculate(Map.of(), List.of(answer("a", 1, "q1", "예"))))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 정의_JSON에서_섹션_순서대로_문항을_읽는다() {
		Map<String, Object> definition = Map.of("sections",
				List.of(Map.of("questions",
						List.of(Map.of("question_id", "q2", "question_note", "학년", "question_type", "multiple_choice",
								"options", List.of("1학년", "2학년")))),
						Map.of("questions", List.of(Map.of("question_id", "q4", "question_note", "자기소개",
								"question_type", "long_answer", "options", List.of())))));

		assertThat(DefinitionQuestion.listOf(definition)).containsExactly(
				choice("q2", "학년", "multiple_choice", "1학년", "2학년"),
				new DefinitionQuestion("q4", "자기소개", "long_answer", List.of()));
		assertThat(DefinitionQuestion.listOf(Map.of())).isEmpty();
	}

	private static DefinitionQuestion choice(String id, String note, String type, String... options) {
		return new DefinitionQuestion(id, note, type, List.of(options));
	}

	private static ResponseRecord answer(String id, int version, String questionId, String... values) {
		return new ResponseRecord(id, version, ResponseStatus.SUBMITTED, Map.of(questionId, List.of(values)));
	}

	private static Map<String, Integer> linked(Object... pairs) {
		Map<String, Integer> map = new java.util.LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

}
