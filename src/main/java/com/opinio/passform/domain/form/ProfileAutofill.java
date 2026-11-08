package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 폼 정의와 프로필 값으로 자동채우기 제안을 만든다(AC6). 대상은 profile_key로만 정한다. 문구나 input_format을 보지 않는다.
public final class ProfileAutofill {

	private static final Set<String> SINGLE_CHOICE = Set.of("multiple_choice", "dropdown");

	private static final String CHECKBOX = "checkbox";

	private ProfileAutofill() {
	}

	public record Suggestion(String questionId, Object value) {
	}

	public record Result(List<Suggestion> answers, List<String> unfilled) {
	}

	// 정의 순서로 돌려준다.
	public static Result suggest(Map<String, Object> definition, Map<String, String> personalInfo) {
		List<Suggestion> answers = new ArrayList<>();
		List<String> unfilled = new ArrayList<>();
		for (Object section : FormJson.array(definition.get("sections"))) {
			for (Object item : FormJson.array(FormJson.object(section).get("questions"))) {
				Map<String, Object> question = FormJson.object(item);
				String questionId = FormJson.text(question.get("question_id"));
				Object value = valueFor(question, personalInfo);
				if (value == null) {
					unfilled.add(questionId);
				}
				else {
					answers.add(new Suggestion(questionId, value));
				}
			}
		}
		return new Result(List.copyOf(answers), List.copyOf(unfilled));
	}

	private static Object valueFor(Map<String, Object> question, Map<String, String> personalInfo) {
		String profileKey = FormJson.text(question.get("profile_key"));
		String value = profileKey == null ? null : personalInfo.get(profileKey);
		if (value == null) {
			return null;
		}
		String type = FormJson.text(question.get("question_type"));
		if (SINGLE_CHOICE.contains(type) || CHECKBOX.equals(type)) {
			// 선택형은 프로필 값이 선택지와 정확히 같을 때만 채운다.
			if (!FormJson.array(question.get("options")).contains(value)) {
				return null;
			}
			// 사람이 정한 것(2026-11-08): checkbox는 저장 형식과 같은 한 항목 배열이다.
			return CHECKBOX.equals(type) ? List.of(value) : value;
		}
		return value;
	}

}
