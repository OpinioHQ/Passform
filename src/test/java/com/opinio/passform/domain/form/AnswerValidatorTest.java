package com.opinio.passform.domain.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.InvalidRequestException;

class AnswerValidatorTest {

	private final FormDefinition definition = FormJson.parseDefinition("""
			{"title":"t","sections":[{"section_id":"s1","title":"1","questions":[
			 {"question_id":"name","question_note":"이름","question_type":"short_answer","required":true,"input_format":{"kind":"name"}},
			 {"question_id":"phone","question_note":"연락처","question_type":"short_answer","input_format":{"kind":"mobile_phone"}},
			 {"question_id":"mail","question_note":"메일","question_type":"short_answer","input_format":{"kind":"email"}},
			 {"question_id":"day","question_note":"날짜","question_type":"short_answer","input_format":{"kind":"date"}},
			 {"question_id":"exp","question_note":"경험","question_type":"multiple_choice","options":["예","아니요"],
			  "branch_rules":[{"when":{"match":"all","conditions":[{"question_id":"exp","option":"아니요"}]},"go_to":{"section_id":"s2"}}]},
			 {"question_id":"role","question_note":"역할","question_type":"multiple_choice","options":["팀장"],"required":true}]},
			 {"section_id":"s2","title":"2","questions":[
			 {"question_id":"area","question_note":"분야","question_type":"checkbox","options":["기획","개발"]},
			 {"question_id":"memo","question_note":"메모","question_type":"long_answer","character_limit":3}]}]}
			""");

	private static Map<String, List<String>> base(Map<String, List<String>> extra) {
		Map<String, List<String>> answers = new java.util.HashMap<>(Map.of("name", List.of("  김  "), "exp", List.of("아니요")));
		answers.putAll(extra);
		return answers;
	}

	private void assertRejected(Map<String, List<String>> answers, String error, String questionId) {
		assertThatThrownBy(() -> AnswerValidator.validate(definition, answers))
			.isInstanceOfSatisfying(InvalidRequestException.class, exception -> {
				assertThat(exception.getMessage()).isEqualTo(error);
				assertThat(exception.getQuestionIds()).containsExactly(questionId);
			});
	}

	@Test
	void 경로_밖의_답은_버리고_형식대로_저장한다() {
		Map<String, List<String>> kept = AnswerValidator.validate(definition,
				base(Map.of("role", List.of("팀장"), "phone", List.of("010-1234 5678"), "day", List.of("2026-11-04"))));
		assertThat(kept).containsEntry("name", List.of("김"))
			.containsEntry("phone", List.of("01012345678"))
			.containsEntry("day", List.of("2026-11-04"))
			.doesNotContainKey("role");
	}

	@Test
	void 경로_위의_필수_문항을_비우면_거부한다() {
		assertRejected(Map.of("exp", List.of("아니요")), "required_missing", "name");
		assertRejected(Map.of("name", List.of("김"), "exp", List.of("예")), "required_missing", "role");
	}

	@Test
	void 건너뛴_필수_문항은_요구하지_않는다() {
		assertThat(AnswerValidator.validate(definition, base(Map.of()))).doesNotContainKey("role");
	}

	@Test
	void 없는_선택지와_단일_선택의_복수_값을_거부한다() {
		assertRejected(base(Map.of("exp", List.of("몰라"), "role", List.of("팀장"))), "invalid_option", "exp");
		assertRejected(base(Map.of("exp", List.of("예", "아니요"))), "invalid_option", "exp");
		assertRejected(base(Map.of("area", List.of("없는값"))), "invalid_option", "area");
	}

	@Test
	void 입력_형식이_맞지_않으면_거부한다() {
		assertRejected(base(Map.of("phone", List.of("12345"))), "invalid_input_format_value", "phone");
		assertRejected(base(Map.of("phone", List.of("010123456"))), "invalid_input_format_value", "phone");
		assertRejected(base(Map.of("phone", List.of("0101234567"))), "invalid_input_format_value", "phone");
		assertRejected(base(Map.of("phone", List.of("1234567890"))), "invalid_input_format_value", "phone");
		assertRejected(base(Map.of("phone", List.of("01212345678"))), "invalid_input_format_value", "phone");
		assertThat(AnswerValidator.validate(definition, base(Map.of("phone", List.of("011-123-4567"))))).containsEntry("phone",
				List.of("0111234567"));
		assertThat(AnswerValidator.validate(definition, base(Map.of("phone", List.of("019-1234-5678"))))).containsEntry("phone",
				List.of("01912345678"));
		assertRejected(base(Map.of("mail", List.of("a@b"))), "invalid_input_format_value", "mail");
		assertRejected(base(Map.of("day", List.of("2026-02-30"))), "invalid_input_format_value", "day");
		assertThat(AnswerValidator.validate(definition, base(Map.of("mail", List.of(" a@b.co "))))).containsEntry("mail",
				List.of("a@b.co"));
	}

	@Test
	void 글자_수_상한을_넘으면_거부한다() {
		assertRejected(base(Map.of("memo", List.of("네글자임"))), "character_limit_exceeded", "memo");
		assertThat(AnswerValidator.validate(definition, base(Map.of("memo", List.of("세글자"))))).containsKey("memo");
	}

}
