package com.opinio.passform.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 테스트용 폼 정의 JSON을 만든다. 모양은 form.schema.json과 같다.
public final class FormDefinitions {

	private FormDefinitions() {
	}

	public static Map<String, Object> definition(String mode, List<Map<String, Object>> sections) {
		return definition(mode, false, sections);
	}

	public static Map<String, Object> definition(String mode, boolean aiSummary, List<Map<String, Object>> sections) {
		Map<String, Object> settings = new LinkedHashMap<>();
		settings.put("editable", false);
		settings.put("cancellable", false);
		settings.put("display", Map.of("default_mode", mode));
		if (aiSummary) {
			settings.put("ai_summary", Map.of("enabled", true));
		}
		return Map.of("title", "가상 폼", "settings", settings, "sections", sections);
	}

	@SafeVarargs
	public static Map<String, Object> section(String id, Map<String, Object>... questions) {
		return Map.of("section_id", id, "questions", List.of(questions));
	}

	@SafeVarargs
	public static Map<String, Object> question(String id, String type, boolean required, List<String> options,
			Map<String, Object>... rules) {
		Map<String, Object> question = new LinkedHashMap<>();
		question.put("question_id", id);
		question.put("question_note", id + " 문구");
		question.put("question_type", type);
		question.put("options", options);
		question.put("required", required);
		question.put("branch_rules", List.of(rules));
		return question;
	}

	public static Map<String, Object> text(String id, boolean required) {
		return question(id, "short_answer", required, List.of());
	}

	public static Map<String, Object> formatted(String id, boolean required, String kind) {
		Map<String, Object> question = text(id, required);
		question.put("input_format", Map.of("kind", kind));
		return question;
	}

	public static Map<String, Object> limited(String id, String type, int limit) {
		Map<String, Object> question = question(id, type, false, List.of());
		question.put("character_limit", limit);
		return question;
	}

	@SafeVarargs
	public static Map<String, Object> rule(String match, Map<String, Object> goTo, Map<String, Object>... conditions) {
		return Map.of("when", Map.of("match", match, "conditions", List.of(conditions)), "go_to", goTo);
	}

	public static Map<String, Object> when(String questionId, String option) {
		return Map.of("question_id", questionId, "option", option);
	}

	public static Map<String, Object> toSection(String id) {
		return Map.of("section_id", id);
	}

	public static Map<String, Object> toQuestion(String id) {
		return Map.of("question_id", id);
	}

	public static Map<String, Object> toSubmit() {
		return Map.of("submit", true);
	}

}
