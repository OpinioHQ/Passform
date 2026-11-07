package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 배포된 폼 정의 JSON(form.schema.json)에서 읽은 문항 하나. 결과 통계와 응답 상세가 문항 문구·유형·선택지를 이 값으로 읽는다.
public record DefinitionQuestion(String questionId, String questionNote, String questionType, List<String> options) {

	private static final Set<String> CHOICE_TYPES = Set.of("multiple_choice", "dropdown", "checkbox");

	public DefinitionQuestion {
		options = List.copyOf(options);
	}

	// 단일 선택(multiple_choice, dropdown)과 복수 선택(checkbox)이다(AGENTS 2절).
	public boolean isChoice() {
		return CHOICE_TYPES.contains(questionType);
	}

	// 섹션 순서 → 섹션 안 문항 순서로 돌려준다.
	public static List<DefinitionQuestion> listOf(Map<String, Object> definition) {
		List<DefinitionQuestion> questions = new ArrayList<>();
		for (Object section : FormJson.array(definition.get("sections"))) {
			for (Object item : FormJson.array(FormJson.object(section).get("questions"))) {
				Map<String, Object> question = FormJson.object(item);
				List<String> options = FormJson.array(question.get("options"))
					.stream()
					.map(FormJson::text)
					.filter(Objects::nonNull)
					.toList();
				questions.add(new DefinitionQuestion(FormJson.text(question.get("question_id")),
						FormJson.text(question.get("question_note")), FormJson.text(question.get("question_type")),
						options));
			}
		}
		return questions;
	}

}
