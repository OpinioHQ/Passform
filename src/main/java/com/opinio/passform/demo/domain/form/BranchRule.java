package com.opinio.passform.demo.domain.form;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public record BranchRule(When when, @JsonProperty("go_to") JsonNode goTo) {

	public boolean matches(Map<String, List<String>> answers) {
		if (when == null || when.conditions() == null || when.conditions().isEmpty()) {
			return false;
		}
		// checkbox 조건은 "그 선택지를 포함함"이다(SPEC 5절 178행).
		if ("any".equals(when.match())) {
			return when.conditions().stream().anyMatch(condition -> condition.holds(answers));
		}
		return when.conditions().stream().allMatch(condition -> condition.holds(answers));
	}

	public boolean goesToSubmit() {
		return goTo != null && (goTo.isString() && "submit".equals(goTo.asString())
				|| goTo.isObject() && goTo.path("submit").asBoolean(false));
	}

	public String targetSectionId() {
		return (goTo != null && goTo.hasNonNull("section_id")) ? goTo.get("section_id").asString() : null;
	}

	public String targetQuestionId() {
		return (goTo != null && goTo.hasNonNull("question_id")) ? goTo.get("question_id").asString() : null;
	}

	public record When(String match, List<Condition> conditions) {
	}

	public record Condition(@JsonProperty("question_id") String questionId, String option) {

		boolean holds(Map<String, List<String>> answers) {
			return answers.getOrDefault(questionId, List.of()).contains(option);
		}

	}

}
