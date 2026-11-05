package com.opinio.passform.domain.form;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.response.Answer;

// 경로 위 문항의 답을 검증하고 저장할 값으로 바꾼다. 오류에는 문항 ID만 담고 답 값은 담지 않는다.
// 검사 순서: 값 모양 → 필수 → 선택지 → 입력 형식 → 글자 수. 같은 오류의 문항은 정의 순서로 모두 담는다.
public final class AnswerValidator {

	private AnswerValidator() {
	}

	// 받은 답을 문항별로 묶는다. 폼에 없거나 경로 밖인 문항의 답은 여기서 버려 어떤 검사도 받지 않는다.
	public static List<Answer> validate(List<Question> onPath, List<Answer> submitted) {
		Map<String, List<Answer>> byQuestion = new LinkedHashMap<>();
		onPath.forEach(question -> byQuestion.put(question.questionId(), new ArrayList<>()));
		for (Answer answer : submitted) {
			List<Answer> sameQuestion = byQuestion.get(answer.questionId());
			if (sameQuestion != null) {
				sameQuestion.add(answer);
			}
		}

		reject(ErrorCode.INVALID_REQUEST, onPath, question -> !wellFormed(question, byQuestion.get(question.questionId())));
		Map<String, Answer> answers = new LinkedHashMap<>();
		for (Question question : onPath) {
			List<Answer> sameQuestion = byQuestion.get(question.questionId());
			if (!sameQuestion.isEmpty() && !sameQuestion.get(0).values().isEmpty()) {
				answers.put(question.questionId(), sameQuestion.get(0));
			}
		}

		reject(ErrorCode.REQUIRED_MISSING, onPath,
				question -> question.required() && !answers.containsKey(question.questionId()));
		List<Question> answered = onPath.stream().filter(question -> answers.containsKey(question.questionId())).toList();
		reject(ErrorCode.INVALID_OPTION, answered,
				question -> (question.isCheckbox() || question.isSingleChoice())
						&& !question.options().containsAll(answers.get(question.questionId()).values()));
		reject(ErrorCode.INVALID_INPUT_FORMAT_VALUE, answered,
				question -> question.inputFormatKind() != null && normalize(question.inputFormatKind(),
						(String) answers.get(question.questionId()).value()).isEmpty());
		reject(ErrorCode.CHARACTER_LIMIT_EXCEEDED, answered,
				question -> question.characterLimit() != null
						&& ((String) answers.get(question.questionId()).value()).length() > question.characterLimit());

		return answered.stream().map(question -> saved(question, answers.get(question.questionId()))).toList();
	}

	private static void reject(ErrorCode code, List<Question> questions, Predicate<Question> invalid) {
		List<String> ids = questions.stream().filter(invalid).map(Question::questionId).toList();
		if (!ids.isEmpty()) {
			throw new DomainException(code, Map.of("question_ids", ids));
		}
	}

	// 문항 유형에 맞는 값 모양인지 본다. null은 비운 답이다. 같은 문항의 답 두 개, checkbox 안의 같은 값은 받지 않는다.
	private static boolean wellFormed(Question question, List<Answer> sameQuestion) {
		if (sameQuestion.size() > 1) {
			return false;
		}
		if (sameQuestion.isEmpty() || sameQuestion.get(0).value() == null) {
			return true;
		}
		Object value = sameQuestion.get(0).value();
		if (!question.isCheckbox()) {
			return value instanceof String;
		}
		if (!(value instanceof List<?> items) || !items.stream().allMatch(String.class::isInstance)) {
			return false;
		}
		List<String> values = sameQuestion.get(0).values();
		return new HashSet<>(values).size() == values.size();
	}

	private static Answer saved(Question question, Answer answer) {
		if (question.isCheckbox()) {
			return new Answer(question.questionId(), answer.values());
		}
		String value = (String) answer.value();
		if (question.inputFormatKind() == null) {
			return new Answer(question.questionId(), value);
		}
		return new Answer(question.questionId(), normalize(question.inputFormatKind(), value).orElseThrow());
	}

	// SPEC 5절 161~164행의 kind별 규칙. 맞지 않으면 빈 값이다.
	static Optional<String> normalize(String kind, String value) {
		return switch (kind) {
			case "name", "address", "student_id" -> Optional.of(value.strip());
			case "mobile_phone" -> digits(value).filter(AnswerValidator::validMobilePhone);
			case "landline_phone" -> digits(value).filter(number -> number.length() >= 9 && number.length() <= 11);
			case "email" -> Optional.of(value.strip()).filter(AnswerValidator::validEmail);
			case "date" -> Optional.of(value).filter(AnswerValidator::validDate);
			default -> throw new IllegalArgumentException("알 수 없는 입력 형식: " + kind);
		};
	}

	private static Optional<String> digits(String value) {
		String number = value.replaceAll("[\\s-]", "");
		return number.matches("[0-9]+") ? Optional.of(number) : Optional.empty();
	}

	private static boolean validMobilePhone(String number) {
		if (number.startsWith("010")) {
			return number.length() == 11;
		}
		return number.matches("01[16-9][0-9]{7,8}");
	}

	private static boolean validEmail(String email) {
		return email.matches("[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+");
	}

	private static boolean validDate(String date) {
		if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			return false;
		}
		try {
			LocalDate.parse(date);
			return true;
		}
		catch (DateTimeParseException e) {
			return false;
		}
	}

}
