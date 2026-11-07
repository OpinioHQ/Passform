package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.opinio.passform.domain.form.FormDefinition.BranchRule;
import com.opinio.passform.domain.form.FormDefinition.GoTo;
import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.form.FormDefinition.Section;

// 답으로 응답 경로를 계산한다(SPEC 5절 178행). 제출 검증, 미리보기, 분석이 함께 쓴다. 클라이언트가 보낸 경로는 입력이 아니다.
public final class ResponsePathCalculator {

	private ResponsePathCalculator() {
	}

	public record ResponsePath(List<Question> onPath, List<Question> skipped) {
	}

	public static ResponsePath calculate(FormDefinition definition, Map<String, List<String>> answers) {
		List<Question> questions = definition.questions();
		boolean sectionPage = FormDefinition.SECTION_PAGE.equals(definition.displayMode());
		List<Question> onPath = new ArrayList<>();
		// 경로에서 만난 문항의 답만 분기 조건에 쓴다. 건너뛴 문항에 보낸 답이 경로를 바꾸면 안 된다.
		Map<String, List<String>> visited = new HashMap<>();
		int index = 0;
		while (index < questions.size()) {
			int last = sectionPage ? lastIndexOfSection(definition, index) : index;
			for (Question question : questions.subList(index, last + 1)) {
				onPath.add(question);
				visited.put(question.questionId(), answers.getOrDefault(question.questionId(), List.of()));
			}
			GoTo goTo = questions.get(last).branchRules().stream()
				.filter(rule -> rule.matches(visited))
				.map(BranchRule::goTo)
				.findFirst()
				.orElse(null);
			if (goTo != null && goTo.submit()) {
				break;
			}
			// 대상은 뒤쪽이어야 한다(배포 때 검증). 뒤쪽이 아니거나 없는 대상이 와도 다음으로 넘어가 반복에 빠지지 않는다.
			int target = goTo == null ? -1 : targetIndex(definition, goTo);
			index = target > last ? target : last + 1;
		}
		Set<String> onPathIds = new HashSet<>();
		onPath.forEach(question -> onPathIds.add(question.questionId()));
		List<Question> skipped = questions.stream()
			.filter(question -> !onPathIds.contains(question.questionId()))
			.toList();
		return new ResponsePath(List.copyOf(onPath), skipped);
	}

	private static int lastIndexOfSection(FormDefinition definition, int index) {
		int end = 0;
		for (Section section : definition.sections()) {
			end += section.questions().size();
			if (index < end) {
				return end - 1;
			}
		}
		throw new IllegalArgumentException("문항 위치가 정의 밖이다");
	}

	// 대상의 전체 문항 순서 위치. 빈 섹션이면 그다음 문항 위치이고, 찾지 못하면 -1이다.
	private static int targetIndex(FormDefinition definition, GoTo goTo) {
		int index = 0;
		for (Section section : definition.sections()) {
			if (section.sectionId().equals(goTo.sectionId())) {
				return index;
			}
			for (Question question : section.questions()) {
				if (question.questionId().equals(goTo.questionId())) {
					return index;
				}
				index++;
			}
		}
		return -1;
	}

}
