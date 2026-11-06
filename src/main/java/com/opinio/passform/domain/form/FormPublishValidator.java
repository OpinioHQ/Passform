package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// 의미 검증(ARCHITECTURE 10절 F4, AC8, AC46). 배포할 때와 open·closed 폼을 저장할 때만 한다. 동의 템플릿은 저장소가 필요해 service가 본다.
// 위반이 여럿이면 SPEC 117행의 순서대로 먼저 걸린 코드를 돌려준다.
public final class FormPublishValidator {

	private static final Set<String> CHOICE_TYPES = Set.of("multiple_choice", "dropdown", "checkbox");

	private static final String ONE_QUESTION = "one_question";

	private static final String ALL_QUESTIONS = "all_questions";

	private static final String SECTION_PAGE = "section_page";

	private FormPublishValidator() {
	}

	public static void validate(Map<String, Object> definition) {
		List<Question> questions = flatten(definition);
		checkQuestions(definition, questions);
		checkBranches(definition, questions);
		checkDisplayMode(definition, questions);
	}

	// 문구, 선택지, ID 중복은 SPEC에 맞는 코드가 없어 schema_invalid로 답한다(사람 결정, 이슈 #40).
	private static void checkQuestions(Map<String, Object> definition, List<Question> questions) {
		Set<Object> sectionIds = new HashSet<>();
		for (Object section : FormJson.array(definition.get("sections"))) {
			if (!sectionIds.add(FormJson.object(section).get("section_id"))) {
				throw new DomainException(ErrorCode.SCHEMA_INVALID);
			}
		}
		Set<String> questionIds = new HashSet<>();
		for (Question question : questions) {
			boolean noOptions = CHOICE_TYPES.contains(question.type()) && question.options().isEmpty();
			if (!questionIds.add(question.id()) || question.note() == null || question.note().isBlank() || noOptions) {
				throw new DomainException(ErrorCode.SCHEMA_INVALID);
			}
		}
	}

	private static void checkBranches(Map<String, Object> definition, List<Question> questions) {
		Map<String, Integer> sectionIndexes = new HashMap<>();
		List<?> sections = FormJson.array(definition.get("sections"));
		for (int i = 0; i < sections.size(); i++) {
			sectionIndexes.put(FormJson.text(FormJson.object(sections.get(i)).get("section_id")), i);
		}
		Map<String, Question> byId = new HashMap<>();
		questions.forEach(question -> byId.put(question.id(), question));

		for (Question question : questions) {
			for (Map<String, Object> rule : question.rules()) {
				checkConditions(rule, question, byId);
				checkTarget(FormJson.object(rule.get("go_to")), question, byId, sectionIndexes);
			}
		}
	}

	// 규칙이 붙은 문항과 그보다 앞의 선택형 문항, 그 문항에 있는 선택지만 가리킬 수 있다(SPEC 178행).
	private static void checkConditions(Map<String, Object> rule, Question owner, Map<String, Question> byId) {
		for (Object value : FormJson.array(FormJson.object(rule.get("when")).get("conditions"))) {
			Map<String, Object> condition = FormJson.object(value);
			Question target = byId.get(FormJson.text(condition.get("question_id")));
			boolean valid = target != null && target.position() <= owner.position()
					&& CHOICE_TYPES.contains(target.type()) && target.options().contains(condition.get("option"));
			if (!valid) {
				throw new DomainException(ErrorCode.INVALID_BRANCH);
			}
		}
	}

	private static void checkTarget(Map<String, Object> goTo, Question owner, Map<String, Question> byId,
			Map<String, Integer> sectionIndexes) {
		if (Boolean.TRUE.equals(goTo.get("submit"))) {
			return;
		}
		boolean valid;
		if (goTo.containsKey("section_id")) {
			Integer target = sectionIndexes.get(FormJson.text(goTo.get("section_id")));
			valid = target != null && target > owner.sectionIndex();
		}
		else {
			Question target = byId.get(FormJson.text(goTo.get("question_id")));
			valid = target != null && target.position() > owner.position();
		}
		if (!valid) {
			throw new DomainException(ErrorCode.INVALID_BRANCH);
		}
	}

	private static void checkDisplayMode(Map<String, Object> definition, List<Question> questions) {
		String mode = defaultMode(definition);
		boolean branched = questions.stream().anyMatch(question -> !question.rules().isEmpty());
		boolean allowed = branched ? ONE_QUESTION.equals(mode) || SECTION_PAGE.equals(mode)
				: ONE_QUESTION.equals(mode) || ALL_QUESTIONS.equals(mode);
		if (!allowed || (SECTION_PAGE.equals(mode) && !sectionPageRulesAtEnd(questions))) {
			throw new DomainException(ErrorCode.INVALID_DISPLAY_MODE);
		}
	}

	// section_page의 규칙은 섹션의 마지막 문항에만 두고 문항으로 이동하지 못한다(SPEC 178행, AC46).
	private static boolean sectionPageRulesAtEnd(List<Question> questions) {
		return questions.stream()
			.filter(question -> !question.rules().isEmpty())
			.allMatch(question -> question.lastInSection() && question.rules()
				.stream()
				.noneMatch(rule -> FormJson.object(rule.get("go_to")).containsKey("question_id")));
	}

	public static String defaultMode(Map<String, Object> definition) {
		return FormJson.text(
				FormJson.object(FormJson.object(definition.get("settings")).get("display")).get("default_mode"));
	}

	public static boolean hasBranchRules(Map<String, Object> definition) {
		return flatten(definition).stream().anyMatch(question -> !question.rules().isEmpty());
	}

	private static List<Question> flatten(Map<String, Object> definition) {
		List<Question> questions = new ArrayList<>();
		List<?> sections = FormJson.array(definition.get("sections"));
		for (int s = 0; s < sections.size(); s++) {
			List<?> inSection = FormJson.array(FormJson.object(sections.get(s)).get("questions"));
			for (int q = 0; q < inSection.size(); q++) {
				Map<String, Object> question = FormJson.object(inSection.get(q));
				List<Map<String, Object>> rules = FormJson.array(question.get("branch_rules"))
					.stream()
					.map(FormJson::object)
					.toList();
				questions.add(new Question(FormJson.text(question.get("question_id")),
						FormJson.text(question.get("question_note")), FormJson.text(question.get("question_type")),
						FormJson.array(question.get("options")), rules, s, questions.size(), q == inSection.size() - 1));
			}
		}
		return questions;
	}

	private record Question(String id, String note, String type, List<?> options, List<Map<String, Object>> rules,
			int sectionIndex, int position, boolean lastInSection) {
	}

}
