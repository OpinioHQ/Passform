package com.opinio.passform.demo.domain.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// SPEC 5절 178행: 제출 검증과 조건 판정이 같이 쓰는 응답 경로 계산. 클라이언트가 보낸 경로를 믿지 않는다.
public final class ResponsePathCalculator {

	private ResponsePathCalculator() {
	}

	public static List<Question> path(FormDefinition definition, Map<String, List<String>> answers) {
		List<Question> ordered = definition.questions();
		List<Question> path = new ArrayList<>();
		int index = 0;
		while (index < ordered.size()) {
			Question current = ordered.get(index);
			path.add(current);
			BranchRule rule = current.branchRules().stream().filter(candidate -> candidate.matches(answers))
				.findFirst().orElse(null);
			if (rule == null) {
				index++;
				continue;
			}
			if (rule.goesToSubmit()) {
				break;
			}
			int target = targetIndex(definition, ordered, rule);
			// 분기는 뒤쪽으로만 간다. 정의가 잘못돼도 무한 반복하지 않게 다음 문항으로 넘어간다.
			index = (target > index) ? target : index + 1;
		}
		return path;
	}

	public static Set<String> skippedQuestionIds(FormDefinition definition, Map<String, List<String>> answers) {
		Set<String> onPath = path(definition, answers).stream().map(Question::questionId).collect(Collectors.toSet());
		return definition.questions().stream().map(Question::questionId).filter(id -> !onPath.contains(id))
			.collect(Collectors.toSet());
	}

	private static int targetIndex(FormDefinition definition, List<Question> ordered, BranchRule rule) {
		String questionId = rule.targetQuestionId();
		if (questionId == null && rule.targetSectionId() != null) {
			questionId = definition.sections().stream()
				.filter(section -> section.sectionId().equals(rule.targetSectionId()))
				.flatMap(section -> section.questions().stream()).map(Question::questionId).findFirst().orElse(null);
		}
		for (int index = 0; index < ordered.size(); index++) {
			if (ordered.get(index).questionId().equals(questionId)) {
				return index;
			}
		}
		return -1;
	}

}
