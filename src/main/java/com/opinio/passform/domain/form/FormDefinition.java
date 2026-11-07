package com.opinio.passform.domain.form;

import java.util.List;
import java.util.Map;

// form_versions.definition(폼 정의 JSON)에서 제출 판정에 쓰는 값만 읽은 것이다. 저장 형식은 그대로 JSON이다.
public record FormDefinition(String displayMode, boolean aiSummaryEnabled, List<Section> sections) {

	public static final String SECTION_PAGE = "section_page";

	public record Section(String sectionId, List<Question> questions) {
	}

	public record Question(String questionId, String questionNote, String questionType, List<String> options,
			boolean required, String inputFormatKind, Integer characterLimit, List<BranchRule> branchRules) {

		public boolean isCheckbox() {
			return "checkbox".equals(questionType);
		}

		public boolean isSingleChoice() {
			return "multiple_choice".equals(questionType) || "dropdown".equals(questionType);
		}

	}

	public record BranchRule(boolean matchAll, List<Condition> conditions, GoTo goTo) {

		public boolean matches(Map<String, List<String>> answers) {
			return matchAll ? conditions.stream().allMatch(condition -> condition.holds(answers))
					: conditions.stream().anyMatch(condition -> condition.holds(answers));
		}

	}

	// checkbox도 단일 선택도 "그 선택지를 포함함"으로 판정한다(SPEC 5절 178행).
	public record Condition(String questionId, String option) {

		boolean holds(Map<String, List<String>> answers) {
			return answers.getOrDefault(questionId, List.of()).contains(option);
		}

	}

	// section_id, question_id, submit 중 하나만 있다.
	public record GoTo(String sectionId, String questionId, boolean submit) {
	}

	public List<Question> questions() {
		return sections.stream().flatMap(section -> section.questions().stream()).toList();
	}

	public static FormDefinition from(Map<String, Object> definition) {
		Map<String, Object> settings = map(definition.get("settings"));
		Map<String, Object> display = map(settings.get("display"));
		Map<String, Object> aiSummary = map(settings.get("ai_summary"));
		List<Section> sections = list(definition.get("sections")).stream().map(FormDefinition::section).toList();
		// 설정이 없으면 꺼짐이다(ARCHITECTURE 10절 F2).
		return new FormDefinition((String) display.get("default_mode"), Boolean.TRUE.equals(aiSummary.get("enabled")),
				sections);
	}

	private static Section section(Object value) {
		Map<String, Object> section = map(value);
		return new Section((String) section.get("section_id"),
				list(section.get("questions")).stream().map(FormDefinition::question).toList());
	}

	private static Question question(Object value) {
		Map<String, Object> question = map(value);
		List<String> options = list(question.get("options")).stream().map(String.class::cast).toList();
		String kind = (String) map(question.get("input_format")).get("kind");
		return new Question((String) question.get("question_id"), (String) question.get("question_note"),
				(String) question.get("question_type"), options, Boolean.TRUE.equals(question.get("required")), kind,
				(Integer) question.get("character_limit"),
				list(question.get("branch_rules")).stream().map(FormDefinition::branchRule).toList());
	}

	private static BranchRule branchRule(Object value) {
		Map<String, Object> rule = map(value);
		Map<String, Object> when = map(rule.get("when"));
		Map<String, Object> goTo = map(rule.get("go_to"));
		List<Condition> conditions = list(when.get("conditions")).stream().map(FormDefinition::map)
			.map(condition -> new Condition((String) condition.get("question_id"), (String) condition.get("option")))
			.toList();
		return new BranchRule(!"any".equals(when.get("match")), conditions, new GoTo((String) goTo.get("section_id"),
				(String) goTo.get("question_id"), Boolean.TRUE.equals(goTo.get("submit"))));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return value == null ? Map.of() : (Map<String, Object>) value;
	}

	private static List<?> list(Object value) {
		return value == null ? List.of() : (List<?>) value;
	}

}
