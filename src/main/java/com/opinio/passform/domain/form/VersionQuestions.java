package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 폼 정의 JSON에서 분석이 쓰는 문항 정보만 읽는다. 개인정보·요약 가능 여부는 문구가 아니라 설정값으로 판정한다(SPEC 168·316행).
public record VersionQuestions(int formVersion, List<Question> questions) {

	private static final Set<String> PERSONAL_FORMATS = Set.of("name", "mobile_phone", "landline_phone", "address",
			"student_id", "email");

	public static VersionQuestions of(int formVersion, Map<String, Object> definition) {
		List<Question> questions = new ArrayList<>();
		for (Object section : listAt(definition, "sections")) {
			for (Object question : listAt(asMap(section), "questions")) {
				questions.add(Question.of(asMap(question)));
			}
		}
		return new VersionQuestions(formVersion, List.copyOf(questions));
	}

	public record Question(String questionId, String questionNote, String questionType, List<String> options,
			boolean personal) {

		private static Question of(Map<String, Object> question) {
			List<String> options = listAt(question, "options").stream().map(String::valueOf).toList();
			boolean personal = question.get("profile_key") != null
					|| PERSONAL_FORMATS.contains(asMap(question.get("input_format")).get("kind"));
			return new Question(String.valueOf(question.get("question_id")), String.valueOf(question.get("question_note")),
					String.valueOf(question.get("question_type")), options, personal);
		}

		public boolean isSingleChoice() {
			return "multiple_choice".equals(questionType) || "dropdown".equals(questionType);
		}

		public boolean isChoice() {
			return isSingleChoice() || "checkbox".equals(questionType);
		}

		public boolean isSummarizable() {
			return ("short_answer".equals(questionType) || "long_answer".equals(questionType)) && !personal;
		}

	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	private static List<?> listAt(Map<String, Object> parent, String key) {
		return parent.get(key) instanceof List<?> list ? list : List.of();
	}

}
