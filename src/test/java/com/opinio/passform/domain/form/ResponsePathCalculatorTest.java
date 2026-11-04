package com.opinio.passform.domain.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ResponsePathCalculatorTest {

	private static final String DEFINITION = """
			{"title":"t","sections":[
			 {"section_id":"s1","title":"1","questions":[
			  {"question_id":"q1","question_note":"경험","question_type":"multiple_choice","options":["예","아니요"],
			   "branch_rules":[{"when":{"match":"all","conditions":[{"question_id":"q1","option":"아니요"}]},"go_to":{"section_id":"s3"}}]}]},
			 {"section_id":"s2","title":"2","questions":[
			  {"question_id":"q2","question_note":"역할","question_type":"multiple_choice","options":["팀장","팀원"]}]},
			 {"section_id":"s3","title":"3","questions":[
			  {"question_id":"q3","question_note":"분야","question_type":"checkbox","options":["기획","개발"],
			   "branch_rules":[{"when":{"match":"any","conditions":[{"question_id":"q3","option":"개발"}]},"go_to":"submit"}]},
			  {"question_id":"q4","question_note":"소개","question_type":"long_answer"}]}]}
			""";

	private final FormDefinition definition = FormJson.parseDefinition(DEFINITION);

	private List<String> path(Map<String, List<String>> answers) {
		return ResponsePathCalculator.path(definition, answers).stream().map(Question::questionId).toList();
	}

	@Test
	void 규칙이_맞으면_뒤쪽_섹션으로_건너뛴다() {
		assertThat(path(Map.of("q1", List.of("아니요")))).containsExactly("q1", "q3", "q4");
		assertThat(ResponsePathCalculator.skippedQuestionIds(definition, Map.of("q1", List.of("아니요"))))
			.containsExactly("q2");
	}

	@Test
	void 규칙이_맞지_않으면_다음_문항으로_간다() {
		assertThat(path(Map.of("q1", List.of("예")))).containsExactly("q1", "q2", "q3", "q4");
	}

	@Test
	void checkbox_조건은_선택지_포함이고_submit이면_멈춘다() {
		assertThat(path(Map.of("q1", List.of("예"), "q3", List.of("기획", "개발")))).containsExactly("q1", "q2", "q3");
	}

	@Test
	void 앞쪽으로_가는_잘못된_규칙은_무한_반복하지_않는다() {
		FormDefinition backward = FormJson.parseDefinition("""
				{"title":"t","sections":[{"section_id":"s1","title":"1","questions":[
				 {"question_id":"q1","question_note":"a","question_type":"short_answer"},
				 {"question_id":"q2","question_note":"b","question_type":"multiple_choice","options":["예"],
				  "branch_rules":[{"when":{"match":"all","conditions":[{"question_id":"q2","option":"예"}]},"go_to":{"question_id":"q1"}}]},
				 {"question_id":"q3","question_note":"c","question_type":"short_answer"}]}]}
				""");
		assertThat(ResponsePathCalculator.path(backward, Map.of("q2", List.of("예"))).stream().map(Question::questionId))
			.containsExactly("q1", "q2", "q3");
	}

}
