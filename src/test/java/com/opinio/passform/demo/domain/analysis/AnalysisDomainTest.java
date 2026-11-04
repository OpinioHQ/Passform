package com.opinio.passform.demo.domain.analysis;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.analysis.MatchMode;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.FormJson;
import com.opinio.passform.demo.domain.form.Question;

class AnalysisDomainTest {

	private static ResponseRecord record(String id, int version, Map<String, List<String>> answers) {
		return new ResponseRecord(id, version, ResponseStatus.SUBMITTED, answers);
	}

	private static Question question(String id, String type, List<String> options) {
		return new Question(id, id, null, type, options, false, null, null, null, List.of());
	}

	// ---- ThemeAggregator (AC87)

	@Test
	void 주제는_개수_내림차순이고_기타는_맨_뒤다() {
		Map<String, String> labels = Map.of("r1", "A", "r2", "B", "r3", "A", "r4", "기타", "r5", "B", "r6", "A");
		List<ThemeAggregator.Theme> themes = ThemeAggregator.aggregate(List.of("r1", "r2", "r3", "r4", "r5", "r6"), labels, 6);
		assertThat(themes).extracting(ThemeAggregator.Theme::label).containsExactly("A", "B", "기타");
		assertThat(themes).extracting(ThemeAggregator.Theme::count).containsExactly(3, 2, 1);
		assertThat(themes.get(0).responseIds()).containsExactly("r1", "r3", "r6");
	}

	@Test
	void 개수가_같으면_가장_작은_ID가_앞서고_기타가_가장_많아도_맨_뒤다() {
		Map<String, String> labels = Map.of("r9", "A", "r2", "B", "r1", "기타", "r3", "기타", "r4", "기타");
		assertThat(ThemeAggregator.aggregate(List.of("r9", "r2", "r1", "r3", "r4"), labels, 6))
			.extracting(ThemeAggregator.Theme::label)
			.containsExactly("B", "A", "기타");
	}

	@Test
	void 보내지_않은_응답의_라벨은_세지_않는다() {
		Map<String, String> labels = Map.of("r1", "A", "남의응답", "A");
		List<ThemeAggregator.Theme> themes = ThemeAggregator.aggregate(List.of("r1", "r2"), labels, 6);
		assertThat(themes).singleElement().satisfies(theme -> assertThat(theme.responseIds()).containsExactly("r1"));
	}

	@Test
	void 상한을_넘은_작은_주제는_기타로_합친다() {
		Map<String, String> labels = new LinkedHashMap<>();
		List<String> ids = new ArrayList<>();
		String[] topics = { "A", "A", "A", "B", "B", "C", "D" };
		for (int index = 0; index < topics.length; index++) {
			labels.put("r" + index, topics[index]);
			ids.add("r" + index);
		}
		List<ThemeAggregator.Theme> themes = ThemeAggregator.aggregate(ids, labels, 3);
		assertThat(themes).extracting(ThemeAggregator.Theme::label).containsExactly("A", "B", "기타");
		assertThat(themes.get(2).responseIds()).containsExactly("r5", "r6");
	}

	// ---- OptionStatistics (AC19)

	@Test
	void 통합_통계는_같은_문구_유형의_같은_선택지만_합친다() {
		FormDefinition v1 = FormJson.parseDefinition("""
				{"title":"t","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"q1","question_note":"찬성","question_type":"multiple_choice","options":["예","아니요"]}]}]}""");
		FormDefinition v2 = FormJson.parseDefinition("""
				{"title":"t","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"q1","question_note":"찬성","question_type":"multiple_choice","options":["예","아니요","보류"]}]}]}""");
		FormDefinition v3 = FormJson.parseDefinition("""
				{"title":"t","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"q1","question_note":"문구 바뀜","question_type":"multiple_choice","options":["예"]}]}]}""");
		List<OptionStatistics.Item> items = OptionStatistics.combine(Map.of(1, v1, 2, v2, 3, v3),
				List.of(record("a", 1, Map.of("q1", List.of("예"))), record("b", 2, Map.of("q1", List.of("예"))),
						record("c", 2, Map.of("q1", List.of("보류"))), record("d", 3, Map.of("q1", List.of("예")))));
		assertThat(items).hasSize(2);
		OptionStatistics.Item combined = items.get(0);
		assertThat(combined.formVersions()).containsExactly(1, 2);
		assertThat(combined.options()).extracting(OptionStatistics.OptionCount::label).containsExactly("예", "아니요", "보류");
		assertThat(combined.options()).extracting(OptionStatistics.OptionCount::count).containsExactly(2, 0, 1);
		assertThat(combined.options().get(2).formVersions()).containsExactly(2);
		assertThat(items.get(1).options().get(0).count()).isEqualTo(1);
	}

	// ---- ResponseSorter (AC28, AC61)

	@Test
	void 숫자_정렬은_빈_값을_맨_뒤에_두고_같은_값은_ID순이다() {
		Question age = question("q", "short_answer", List.of());
		List<ResponseRecord> records = List.of(record("r3", 1, Map.of("q", List.of("20"))), record("r1", 1, Map.of()),
				record("r2", 1, Map.of("q", List.of("25"))), record("r0", 1, Map.of("q", List.of("20"))));
		assertThat(ResponseSorter.sort(records, Map.of(1, age), "desc").orElseThrow().responseIds())
			.containsExactly("r2", "r0", "r3", "r1");
		assertThat(ResponseSorter.sort(records, Map.of(1, age), "asc").orElseThrow().responseIds())
			.containsExactly("r0", "r3", "r2", "r1");
	}

	@Test
	void 정규화할_수_없는_값이나_장문은_정렬하지_않는다() {
		Question age = question("q", "short_answer", List.of());
		assertThat(ResponseSorter.sort(List.of(record("r", 1, Map.of("q", List.of("23살")))), Map.of(1, age), "asc")).isEmpty();
		assertThat(ResponseSorter.sort(List.of(record("r", 1, Map.of())), Map.of(1, question("q", "long_answer", List.of())), "asc")).isEmpty();
		assertThat(ResponseSorter.sort(List.of(record("r", 1, Map.of())), Map.of(1, question("q", "checkbox", List.of("a"))), "asc")).isEmpty();
	}

	@Test
	void 날짜와_선택지_순서로_정렬한다() {
		Question date = question("q", "short_answer", List.of());
		List<ResponseRecord> dates = List.of(record("a", 1, Map.of("q", List.of("2026-12-01"))), record("b", 1, Map.of("q", List.of("2026-01-31"))));
		assertThat(ResponseSorter.sort(dates, Map.of(1, date), "asc").orElseThrow().responseIds()).containsExactly("b", "a");

		Question grade = question("q", "multiple_choice", List.of("1학년", "2학년", "3학년"));
		List<ResponseRecord> grades = List.of(record("a", 1, Map.of("q", List.of("3학년"))), record("b", 1, Map.of("q", List.of("1학년"))));
		assertThat(ResponseSorter.sort(grades, Map.of(1, grade), "asc").orElseThrow().responseIds()).containsExactly("b", "a");
	}

	@Test
	void 버전_사이에서_선택지_순서가_다르면_정렬하지_않는다() {
		Question v1 = question("q", "multiple_choice", List.of("A", "B"));
		Question v2 = question("q", "multiple_choice", List.of("B", "A"));
		List<ResponseRecord> records = List.of(record("a", 1, Map.of("q", List.of("A"))), record("b", 2, Map.of("q", List.of("B"))));
		assertThat(ResponseSorter.sort(records, Map.of(1, v1, 2, v2), "asc")).isEmpty();
	}

	@Test
	void 문항이_없는_버전은_맨_뒤에_두고_알린다() {
		Question age = question("q", "short_answer", List.of());
		List<ResponseRecord> records = List.of(record("a", 2, Map.of()), record("b", 1, Map.of("q", List.of("20"))));
		Optional<ResponseSorter.Sorted> sorted = ResponseSorter.sort(records, Map.of(1, age), "asc");
		assertThat(sorted.orElseThrow().responseIds()).containsExactly("b", "a");
		assertThat(sorted.orElseThrow().sortUnavailableVersions()).containsExactly(2);
	}

	@Test
	void 되묻기_후보는_정렬_가능한_문항만_낸다() {
		Question age = question("q", "short_answer", List.of());
		Question name = new Question("n", "이름", null, "short_answer", List.of(), false, null, new Question.InputFormat("name", null), null, List.of());
		List<ResponseRecord> numeric = List.of(record("a", 1, Map.of("q", List.of("20"))));
		assertThat(ResponseSorter.isSortable(age, numeric)).isTrue();
		assertThat(ResponseSorter.isSortable(name, numeric)).isFalse();
		assertThat(ResponseSorter.isSortable(question("q", "long_answer", List.of()), numeric)).isFalse();
		assertThat(ResponseSorter.isSortable(age, List.of(record("a", 1, Map.of("q", List.of("스물")))))).isFalse();
	}

	// ---- AnswerMasker (AC92)

	@Test
	void 답_속_전화번호와_이메일을_가린다() {
		String masked = AnswerMasker.mask("연락은 010-1234-5678, a@example.com으로. 02-123-4567도 됩니다. 학번은 20261234입니다.");
		assertThat(masked).doesNotContain("010-1234-5678", "a@example.com", "02-123-4567").contains("[전화번호]", "[이메일]", "20261234");
	}

	// ---- PathAwareEvaluator (SPEC 5절 313행)

	@Test
	void 분기로_건너뛴_문항은_판정_불가이고_경로상_비운_문항은_거짓이다() {
		FormDefinition definition = FormJson.parseDefinition("""
				{"title":"t","sections":[
				 {"section_id":"s1","title":"1","questions":[
				  {"question_id":"q5","question_note":"경험","question_type":"multiple_choice","options":["예","아니요"],
				   "branch_rules":[{"when":{"match":"all","conditions":[{"question_id":"q5","option":"아니요"}]},"go_to":{"section_id":"s3"}}]}]},
				 {"section_id":"s2","title":"2","questions":[
				  {"question_id":"q6","question_note":"역할","question_type":"multiple_choice","options":["팀장","팀원"]}]},
				 {"section_id":"s3","title":"3","questions":[
				  {"question_id":"q4","question_note":"소개","question_type":"long_answer"}]}]}""");
		List<ResponseRecord> records = List.of(record("leader", 1, Map.of("q5", List.of("예"), "q6", List.of("팀장"))),
				record("member", 1, Map.of("q5", List.of("예"), "q6", List.of("팀원"))),
				record("blank", 1, Map.of("q5", List.of("예"))), record("skipped", 1, Map.of("q5", List.of("아니요"))));
		List<ConditionMatch> matching = List.of(new ConditionMatch("팀장", 1, "q6", MatchKind.OPTION, "팀장"));
		PathAwareEvaluator evaluator = new PathAwareEvaluator();

		EvaluationResult positive = evaluator.evaluate(List.of("팀장"), MatchMode.ALL, matching, records, Map.of(1, definition));
		assertThat(positive.responseIds()).containsExactly("leader");
		assertThat(positive.coverage().notEvaluableCount()).isEqualTo(1);
		assertThat(positive.coverage().notEvaluableByVersion()).singleElement()
			.satisfies(version -> assertThat(version.unavailableConditions()).containsExactly("팀장"));
		assertThat(positive.matched()).hasSize(1);

		EvaluationResult negative = evaluator.evaluate(List.of("팀장"), MatchMode.NOT, matching, records, Map.of(1, definition));
		assertThat(negative.responseIds()).containsExactly("blank", "member");
		assertThat(negative.coverage().totalSubmittedCount()).isEqualTo(4);

		List<ConditionMatch> unanswered = List.of(new ConditionMatch("역할 미응답", 1, "q6", MatchKind.UNANSWERED, null));
		assertThat(evaluator.evaluate(List.of("역할 미응답"), MatchMode.ALL, unanswered, records, Map.of(1, definition)).responseIds())
			.containsExactly("blank");
	}

	@Test
	void any에서_건너뛴_조건이_있어도_다른_조건이_참이면_일치한다() {
		FormDefinition definition = FormJson.parseDefinition("""
				{"title":"t","sections":[
				 {"section_id":"s1","title":"1","questions":[
				  {"question_id":"q5","question_note":"경험","question_type":"multiple_choice","options":["예","아니요"],
				   "branch_rules":[{"when":{"match":"all","conditions":[{"question_id":"q5","option":"아니요"}]},"go_to":"submit"}]}]},
				 {"section_id":"s2","title":"2","questions":[
				  {"question_id":"q6","question_note":"역할","question_type":"multiple_choice","options":["팀장"]}]}]}""");
		List<ResponseRecord> records = List.of(record("skipped", 1, Map.of("q5", List.of("아니요"))));
		List<ConditionMatch> matching = List.of(new ConditionMatch("팀장", 1, "q6", MatchKind.OPTION, "팀장"),
				new ConditionMatch("아니요", 1, "q5", MatchKind.OPTION, "아니요"));
		EvaluationResult result = new PathAwareEvaluator().evaluate(List.of("팀장", "아니요"), MatchMode.ANY, matching, records,
				Map.of(1, definition));
		assertThat(result.responseIds()).containsExactly("skipped");
		assertThat(result.coverage().notEvaluableCount()).isZero();
		assertThat(Set.copyOf(result.responseIds())).hasSize(1);
	}

}
