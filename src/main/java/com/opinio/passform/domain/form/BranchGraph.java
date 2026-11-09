package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.List;

import com.opinio.passform.domain.form.FormDefinition.BranchRule;
import com.opinio.passform.domain.form.FormDefinition.GoTo;
import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.form.FormDefinition.Section;

// 분기 흐름 그래프(SPEC 5절 125행, AC45). 섹션·문항·제출이 노드이고, 규칙마다 rule 엣지, 문항마다 default 엣지가 하나씩이다.
// 기본 다음 이동은 응답 경로 계산(ResponsePathCalculator.nextIndex)을 그대로 쓴다(AGENTS 5절 분기 해석 공통화).
public record BranchGraph(List<Node> nodes, List<Edge> edges) {

	public static final String SUBMIT = "submit";

	public record Node(String id, String type) {
	}

	// ruleIndex는 rule 엣지에만 있고 그 문항의 branch_rules 안 위치(0부터)다.
	public record Edge(String from, String to, String kind, Integer ruleIndex) {
	}

	public static BranchGraph of(FormDefinition definition) {
		List<Node> nodes = new ArrayList<>();
		for (Section section : definition.sections()) {
			nodes.add(new Node(section.sectionId(), "section"));
			section.questions().forEach(question -> nodes.add(new Node(question.questionId(), "question")));
		}
		nodes.add(new Node(SUBMIT, "submit"));

		List<Question> questions = definition.questions();
		List<Edge> edges = new ArrayList<>();
		for (int index = 0; index < questions.size(); index++) {
			Question question = questions.get(index);
			List<BranchRule> rules = question.branchRules();
			for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
				edges.add(new Edge(question.questionId(), declaredTarget(rules.get(ruleIndex).goTo()), "rule", ruleIndex));
			}
			int next = ResponsePathCalculator.nextIndex(definition, index, null);
			edges.add(new Edge(question.questionId(), next < questions.size() ? questions.get(next).questionId() : SUBMIT,
					"default", null));
		}
		return new BranchGraph(List.copyOf(nodes), List.copyOf(edges));
	}

	// 규칙이 가리키는 노드다. 섹션으로 가는 규칙은 섹션 노드로 잇는다. 대상이 실제로 있는지는 배포 때 검증하므로(AC8) 초안에서는 노드에 없는 ID일 수 있다.
	private static String declaredTarget(GoTo goTo) {
		if (goTo.submit()) {
			return SUBMIT;
		}
		return goTo.sectionId() != null ? goTo.sectionId() : goTo.questionId();
	}

}
