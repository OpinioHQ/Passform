package com.opinio.passform.domain.form;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Question(@JsonProperty("question_id") String questionId,
		@JsonProperty("question_note") String questionNote,
		@JsonProperty("question_description") String questionDescription,
		@JsonProperty("question_type") String questionType, List<String> options, Boolean required,
		@JsonProperty("profile_key") String profileKey, @JsonProperty("input_format") InputFormat inputFormat,
		@JsonProperty("character_limit") Integer characterLimit,
		@JsonProperty("branch_rules") List<BranchRule> branchRules) {

	private static final Set<String> PERSONAL_FORMATS = Set.of("name", "mobile_phone", "landline_phone", "address",
			"student_id", "email");

	public Question {
		options = (options != null) ? options : List.of();
		required = Boolean.TRUE.equals(required);
		branchRules = (branchRules != null) ? branchRules : List.of();
	}

	public boolean isSingleChoice() {
		return "multiple_choice".equals(questionType) || "dropdown".equals(questionType);
	}

	public boolean isCheckbox() {
		return "checkbox".equals(questionType);
	}

	public boolean isChoice() {
		return isSingleChoice() || isCheckbox();
	}

	public boolean isText() {
		return "short_answer".equals(questionType) || "long_answer".equals(questionType);
	}

	// SPEC 5절 168행: 개인정보 문항의 값은 LLM·임베딩 요청으로 보내지 않는다.
	public boolean isPersonal() {
		return profileKey != null
				|| (inputFormat != null && inputFormat.kind() != null && PERSONAL_FORMATS.contains(inputFormat.kind()));
	}

	public boolean isSummarizable() {
		return isText() && !isPersonal();
	}

	public record InputFormat(String kind, String placeholder) {
	}

}
