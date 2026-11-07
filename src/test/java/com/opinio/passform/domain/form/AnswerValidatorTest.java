package com.opinio.passform.domain.form;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.formatted;
import static com.opinio.passform.support.FormDefinitions.limited;
import static com.opinio.passform.support.FormDefinitions.question;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.response.Answer;

class AnswerValidatorTest {

	@SafeVarargs
	private static List<Question> questions(Map<String, Object>... questions) {
		return FormDefinition.from(definition("one_question", List.of(section("s1", questions)))).questions();
	}

	private static void assertRejected(ErrorCode code, List<String> ids, List<Question> onPath, Answer... answers) {
		assertThatThrownBy(() -> AnswerValidator.validate(onPath, List.of(answers)))
			.isInstanceOfSatisfying(DomainException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(code);
				assertThat(e.getDetails()).containsEntry("question_ids", ids);
			});
	}

	// AC73 테스트 줄(SPEC 405행)의 값이다.
	@ParameterizedTest
	@CsvSource({ "date,2026-02-28,2026-02-28", "mobile_phone,010-1234-5678,01012345678",
			"mobile_phone,011-123-4567,0111234567", "landline_phone,02-123-4567,021234567",
			"email,a@example.com,a@example.com" })
	void AC73_맞는_값은_규칙대로_저장한다(String kind, String input, String stored) {
		assertThat(AnswerValidator.validate(questions(formatted("q1", true, kind)), List.of(new Answer("q1", input))))
			.containsExactly(new Answer("q1", stored));
	}

	// 9자리·문자 포함·010의 10자리·01로 시작하지 않음·허용하지 않는 앞자리.
	@ParameterizedTest
	@ValueSource(strings = { "011123456", "010-1234-567a", "0101234567", "1234567890", "01212345678" })
	void AC73_휴대전화_규칙에_맞지_않으면_거부한다(String value) {
		assertRejected(ErrorCode.INVALID_INPUT_FORMAT_VALUE, List.of("q1"),
				questions(formatted("q1", true, "mobile_phone")), new Answer("q1", value));
	}

	@Test
	void AC73_없는_날짜와_8자리_집전화와_골뱅이_둘인_이메일을_거부한다() {
		assertRejected(ErrorCode.INVALID_INPUT_FORMAT_VALUE, List.of("q1"), questions(formatted("q1", true, "date")),
				new Answer("q1", "2026-02-30"));
		assertRejected(ErrorCode.INVALID_INPUT_FORMAT_VALUE, List.of("q1"),
				questions(formatted("q1", true, "landline_phone")), new Answer("q1", "02-123-456"));
		assertRejected(ErrorCode.INVALID_INPUT_FORMAT_VALUE, List.of("q1"), questions(formatted("q1", true, "email")),
				new Answer("q1", "a@@example.com"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "name", "address", "student_id" })
	void AC73_이름_주소_학번은_앞뒤_공백을_지워_저장한다(String kind) {
		assertThat(AnswerValidator.validate(questions(formatted("q1", true, kind)), List.of(new Answer("q1", "  가상 값 "))))
			.containsExactly(new Answer("q1", "가상 값"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "name", "address", "student_id", "mobile_phone", "landline_phone", "email", "date" })
	void AC73_필수_문항에_공백만_내면_required_missing이다(String kind) {
		assertRejected(ErrorCode.REQUIRED_MISSING, List.of("q1"), questions(formatted("q1", true, kind)),
				new Answer("q1", "   "));
	}

	// SPEC 162행: 016~019의 10·11자리, 공백 제거, 집전화 11자리 경계.
	@ParameterizedTest
	@CsvSource({ "mobile_phone,016 123 4567,0161234567", "mobile_phone,019-1234-5678,01912345678",
			"landline_phone,031-1234-5678,03112345678" })
	void 전화번호_허용_범위의_경계를_받는다(String kind, String input, String stored) {
		assertThat(AnswerValidator.validate(questions(formatted("q1", true, kind)), List.of(new Answer("q1", input))))
			.containsExactly(new Answer("q1", stored));
	}

	// SPEC 163행: @ 앞이 비어 있음, 점 없는 도메인, 빈 도메인 부분, 공백 포함.
	@ParameterizedTest
	@ValueSource(strings = { "@example.com", "a@example", "a@.com", "a@example.", "a b@example.com" })
	void 이메일_구조에_맞지_않으면_거부한다(String value) {
		assertRejected(ErrorCode.INVALID_INPUT_FORMAT_VALUE, List.of("q1"), questions(formatted("q1", true, "email")),
				new Answer("q1", value));
	}

	@Test
	void 이메일은_앞뒤_공백만_지운다() {
		assertThat(AnswerValidator.validate(questions(formatted("q1", true, "email")),
				List.of(new Answer("q1", " A.b@Example.com "))))
			.containsExactly(new Answer("q1", "A.b@Example.com"));
	}

	@Test
	void AC53_100자는_받고_101자는_거부한다() {
		for (String type : List.of("short_answer", "long_answer")) {
			List<Question> onPath = questions(limited("q1", type, 100));
			assertThat(AnswerValidator.validate(onPath, List.of(new Answer("q1", "가".repeat(100))))).hasSize(1);
			assertRejected(ErrorCode.CHARACTER_LIMIT_EXCEEDED, List.of("q1"), onPath, new Answer("q1", "가".repeat(101)));
		}
	}

	@Test
	void AC53_글자_수는_공백과_줄바꿈을_포함한다() {
		assertRejected(ErrorCode.CHARACTER_LIMIT_EXCEEDED, List.of("q1"), questions(limited("q1", "long_answer", 3)),
				new Answer("q1", "가 \n나"));
	}

	@Test
	void 선택지_밖_값은_invalid_option이다() {
		List<Question> onPath = questions(question("q1", "dropdown", true, List.of("가", "나")),
				question("q2", "checkbox", false, List.of("다", "라")));
		assertRejected(ErrorCode.INVALID_OPTION, List.of("q1", "q2"), onPath, new Answer("q1", "없음"),
				new Answer("q2", List.of("다", "없음")));
	}

	@Test
	void checkbox는_하나만_골라도_배열로_저장하고_비운_문항은_빠진다() {
		List<Question> onPath = questions(question("q1", "checkbox", false, List.of("가", "나")), text("q2", false),
				question("q3", "checkbox", false, List.of("가")), text("q4", false));

		assertThat(AnswerValidator.validate(onPath, List.of(new Answer("q1", List.of("가")), new Answer("q2", ""),
				new Answer("q3", List.of()), new Answer("q4", null))))
			.containsExactly(new Answer("q1", List.of("가")));
	}

	@Test
	void 저장은_정의_순서이고_폼에_없는_문항의_답은_버린다() {
		List<Question> onPath = questions(text("q1", false), text("q2", false));

		assertThat(AnswerValidator.validate(onPath,
				List.of(new Answer("q2", "둘"), new Answer("없음", 1), new Answer("q1", "하나"))))
			.containsExactly(new Answer("q1", "하나"), new Answer("q2", "둘"));
	}

	@Test
	void 같은_오류의_문항은_정의_순서로_모두_담는다() {
		assertRejected(ErrorCode.REQUIRED_MISSING, List.of("q1", "q3"),
				questions(text("q1", true), text("q2", false), text("q3", true)), new Answer("q2", "값"));
	}

	// 리뷰: 선택형의 공백 값은 지우지 않고 선택지 밖 값으로 거부한다.
	@Test
	void 선택형의_공백_값은_invalid_option이다() {
		assertRejected(ErrorCode.INVALID_OPTION, List.of("q1"),
				questions(question("q1", "checkbox", false, List.of("가", "나"))), new Answer("q1", List.of("가", " ")));
		assertRejected(ErrorCode.INVALID_OPTION, List.of("q1"),
				questions(question("q1", "multiple_choice", false, List.of("가", "나"))), new Answer("q1", " "));
		assertRejected(ErrorCode.INVALID_OPTION, List.of("q1"),
				questions(question("q1", "dropdown", true, List.of("가", "나"))), new Answer("q1", "  "));
		assertRejected(ErrorCode.INVALID_OPTION, List.of("q1"),
				questions(question("q1", "checkbox", true, List.of("가", "나"))), new Answer("q1", List.of(" ")));
		assertRejected(ErrorCode.INVALID_OPTION, List.of("q1"),
				questions(question("q1", "checkbox", false, List.of("가", "나"))), new Answer("q1", List.of("가", "")));
	}

	// 지금 동작을 고정한다: 선택형에서 null, 빈 문자열, 빈 배열은 비운 답이다.
	@Test
	void 선택형의_null_빈_문자열_빈_배열은_비운_답이다() {
		List<Question> optional = questions(question("q1", "multiple_choice", false, List.of("가")),
				question("q2", "checkbox", false, List.of("가")), question("q3", "dropdown", false, List.of("가")));
		assertThat(AnswerValidator.validate(optional,
				List.of(new Answer("q1", ""), new Answer("q2", List.of()), new Answer("q3", null)))).isEmpty();

		assertRejected(ErrorCode.REQUIRED_MISSING, List.of("q1", "q2"),
				questions(question("q1", "multiple_choice", true, List.of("가")), question("q2", "checkbox", true, List.of("가"))),
				new Answer("q1", ""), new Answer("q2", List.of()));
	}

	@Test
	void 값_모양이_유형과_다르면_invalid_request다() {
		List<String> options = List.of("가", "나");
		assertRejected(ErrorCode.INVALID_REQUEST, List.of("q1"), questions(question("q1", "checkbox", false, options)),
				new Answer("q1", "가"));
		assertRejected(ErrorCode.INVALID_REQUEST, List.of("q1"),
				questions(question("q1", "multiple_choice", false, options)), new Answer("q1", List.of("가")));
		assertRejected(ErrorCode.INVALID_REQUEST, List.of("q1"), questions(text("q1", false)), new Answer("q1", 5));
		assertRejected(ErrorCode.INVALID_REQUEST, List.of("q1"), questions(question("q1", "checkbox", false, options)),
				new Answer("q1", Arrays.asList("가", 1)));
	}

	@Test
	void 같은_문항의_답이_둘이거나_checkbox_안의_값이_겹치면_invalid_request다() {
		assertRejected(ErrorCode.INVALID_REQUEST, List.of("q1"), questions(text("q1", false)), new Answer("q1", "가"),
				new Answer("q1", "나"));
		assertRejected(ErrorCode.INVALID_REQUEST, List.of("q1"),
				questions(question("q1", "checkbox", false, List.of("가"))), new Answer("q1", List.of("가", "가")));
	}

	@Test
	void 오류에는_답_값을_담지_않는다() {
		assertThatThrownBy(() -> AnswerValidator.validate(questions(formatted("q1", true, "email")),
				List.of(new Answer("q1", "virtual@@example.com"))))
			.isInstanceOfSatisfying(DomainException.class, e -> {
				assertThat(e.getMessage()).doesNotContain("virtual");
				assertThat(e.getDetails().toString()).doesNotContain("virtual");
			});
	}

}
