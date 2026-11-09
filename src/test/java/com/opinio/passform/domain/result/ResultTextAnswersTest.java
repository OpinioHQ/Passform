package com.opinio.passform.domain.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.result.FormResults.CombinedOption;
import com.opinio.passform.domain.result.FormResults.CombinedTextQuestion;
import com.opinio.passform.domain.result.FormResults.QuestionRef;
import com.opinio.passform.domain.result.FormResults.TextAnswer;
import com.opinio.passform.domain.result.FormResults.VersionTextQuestion;

// 사람 결정(2026-11-09): 결과에 단답·장문 답 목록(text_questions)과 문항 순서(question_order)를 더한다. DB 없이 계산만 본다.
class ResultTextAnswersTest {

	private static final DefinitionQuestion ATTEND = new DefinitionQuestion("q1", "참석하나요?", "multiple_choice",
			List.of("예", "아니요"));

	private static final DefinitionQuestion AGE = new DefinitionQuestion("q2", "나이", "short_answer", List.of());

	private static final DefinitionQuestion MOTIVE = new DefinitionQuestion("q3", "지원 동기", "long_answer", List.of());

	private static final Instant DAY1 = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant DAY2 = Instant.parse("2026-11-02T00:00:00Z");

	private static ResponseRecord response(String id, int version, Map<String, List<String>> answers) {
		return new ResponseRecord(id, version, ResponseStatus.SUBMITTED, answers);
	}

	@Test
	void 단답_장문_답이_정의_순서와_함께_들어가고_빈_답은_빠진다() {
		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(AGE, ATTEND, MOTIVE)),
				List.of(response("a", 1, Map.of("q1", List.of("예"), "q2", List.of("21"), "q3", List.of("배우고 싶어요\n열심히"))),
						response("b", 1, Map.of("q1", List.of("아니요"), "q2", List.of("  "))),
						response("c", 1, Map.of("q1", List.of("예")))),
				Map.of("a", DAY1, "b", DAY1, "c", DAY1));

		assertThat(results.combined().textQuestions()).containsExactly(
				new CombinedTextQuestion("q2", "나이", "short_answer", List.of(1),
						List.of(new TextAnswer("a", 1, "21", DAY1))),
				new CombinedTextQuestion("q3", "지원 동기", "long_answer", List.of(1),
						List.of(new TextAnswer("a", 1, "배우고 싶어요\n열심히", DAY1))));
		assertThat(results.combined().questionOrder()).containsExactly(new QuestionRef("q2", "나이", "short_answer"),
				new QuestionRef("q1", "참석하나요?", "multiple_choice"), new QuestionRef("q3", "지원 동기", "long_answer"));
		assertThat(results.versions().get(0).questionOrder()).isEqualTo(results.combined().questionOrder());
	}

	@Test
	void 최신_제출이_위이고_같은_시각이면_response_id_오름차순이다() {
		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(AGE)),
				List.of(response("r3", 1, Map.of("q2", List.of("30"))), response("r2", 1, Map.of("q2", List.of("20"))),
						response("r1", 1, Map.of("q2", List.of("10")))),
				Map.of("r1", DAY1, "r2", DAY2, "r3", DAY2));

		assertThat(results.combined().textQuestions().get(0).answers()).extracting(TextAnswer::responseId)
			.containsExactly("r2", "r3", "r1");
	}

	@Test
	void 같은_문항은_버전을_합치고_문구가_바뀌면_따로이며_버전별로는_그_버전_답만이다() {
		DefinitionQuestion renamed = new DefinitionQuestion("q3", "지원한 이유", "long_answer", List.of());
		FormResults results = ResultStatistics.calculate(Map.of(1, List.of(AGE, MOTIVE), 2, List.of(AGE, renamed)),
				List.of(response("a", 1, Map.of("q2", List.of("21"), "q3", List.of("옛 동기"))),
						response("b", 2, Map.of("q2", List.of("22"), "q3", List.of("새 이유")))),
				Map.of("a", DAY1, "b", DAY2));

		assertThat(results.combined().textQuestions()).extracting(CombinedTextQuestion::questionNote,
				CombinedTextQuestion::formVersions)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("나이", List.of(1, 2)),
					org.assertj.core.groups.Tuple.tuple("지원 동기", List.of(1)),
					org.assertj.core.groups.Tuple.tuple("지원한 이유", List.of(2)));
		assertThat(results.combined().textQuestions().get(0).answers()).extracting(TextAnswer::value)
			.containsExactly("22", "21");
		assertThat(results.versions().get(1).textQuestions()).containsExactly(
				new VersionTextQuestion("q2", "나이", "short_answer", List.of(new TextAnswer("b", 2, "22", DAY2))),
				new VersionTextQuestion("q3", "지원한 이유", "long_answer", List.of(new TextAnswer("b", 2, "새 이유", DAY2))));
	}

	@Test
	void 선택형_통계_값은_그대로다() {
		List<ResponseRecord> responses = List.of(response("a", 1, Map.of("q1", List.of("예"), "q2", List.of("21"))),
				response("b", 1, Map.of("q1", List.of("예"))));

		FormResults withText = ResultStatistics.calculate(Map.of(1, List.of(ATTEND, AGE)), responses,
				Map.of("a", DAY1, "b", DAY1));
		FormResults choiceOnly = ResultStatistics.calculate(Map.of(1, List.of(ATTEND)), responses);

		assertThat(withText.responseCount()).isEqualTo(2);
		assertThat(withText.combined().questions()).isEqualTo(choiceOnly.combined().questions());
		assertThat(withText.combined().questions().get(0).options()).containsExactly(
				new CombinedOption("예", 2, List.of(1)), new CombinedOption("아니요", 0, List.of(1)));
		assertThat(withText.versions().get(0).questions()).isEqualTo(choiceOnly.versions().get(0).questions());
	}

}
