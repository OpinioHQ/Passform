package com.opinio.passform.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.analysis.QuestionResolver;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.Question;
import com.opinio.passform.llm.ConditionLlmMatcher;

// 조건을 버전별 문항·선택지에 연결한다. 글자가 같으면 규칙으로 풀고, 어느 버전에서도 못 푼 조건만 LLM에 묻는다.
@Service
public class ConditionMatchingService {

	private static final Set<String> ANSWERED = Set.of("응답", "답함", "작성함", "답변");

	private static final Set<String> UNANSWERED = Set.of("미응답", "안 씀", "비움", "미작성", "무응답");

	private static final String SEPARATOR = " - ";

	private final ConditionLlmMatcher llmMatcher;

	public ConditionMatchingService(ConditionLlmMatcher llmMatcher) {
		this.llmMatcher = llmMatcher;
	}

	public record Candidate(int formVersion, String questionId, String questionNote) {
	}

	public record Outcome(List<ConditionMatch> entries, List<String> unknownConditions,
			Map<String, List<Candidate>> ambiguous) {
	}

	public Outcome match(List<String> conditions, Map<Integer, FormDefinition> active, FormDefinition latest,
			int latestVersion) {
		Map<String, List<ConditionMatch>> byCondition = new LinkedHashMap<>();
		List<String> unresolved = new ArrayList<>();
		for (String condition : conditions) {
			List<ConditionMatch> hits = new ArrayList<>();
			active.forEach((version, definition) -> hits.addAll(byRule(condition, version, definition, latest)));
			byCondition.put(condition, hits);
			if (hits.isEmpty()) {
				unresolved.add(condition);
			}
		}
		if (!unresolved.isEmpty()) {
			llmMatcher.match(unresolved, active, latestVersion)
				.forEach(entry -> byCondition.get(entry.condition()).add(entry));
		}
		return summarize(conditions, byCondition, active);
	}

	private static Outcome summarize(List<String> conditions, Map<String, List<ConditionMatch>> byCondition,
			Map<Integer, FormDefinition> active) {
		List<ConditionMatch> entries = new ArrayList<>();
		List<String> unknown = new ArrayList<>();
		Map<String, List<Candidate>> ambiguous = new LinkedHashMap<>();
		for (String condition : conditions) {
			List<ConditionMatch> hits = byCondition.get(condition);
			if (hits.isEmpty()) {
				unknown.add(condition);
				continue;
			}
			// 같은 표현이 뜻이 다른 여러 문항에 걸리면 합치지 않고 되묻는다(AC25).
			Set<String> questionIds = hits.stream().map(ConditionMatch::questionId).collect(Collectors.toSet());
			if (questionIds.size() > 1) {
				ambiguous.put(condition,
						hits.stream()
							.map(hit -> new Candidate(hit.formVersion(), hit.questionId(),
									active.get(hit.formVersion()).question(hit.questionId())
										.map(Question::questionNote)
										.orElse(null)))
							.toList());
				continue;
			}
			entries.addAll(hits);
		}
		return new Outcome(entries, unknown, ambiguous);
	}

	private static List<ConditionMatch> byRule(String condition, int version, FormDefinition definition,
			FormDefinition latest) {
		int separator = condition.lastIndexOf(SEPARATOR);
		if (separator > 0) {
			String questionExpression = condition.substring(0, separator).trim();
			String value = condition.substring(separator + SEPARATOR.length()).trim();
			List<ConditionMatch> hits = new ArrayList<>();
			for (Question question : QuestionResolver.resolve(questionExpression, definition, latest)) {
				if (ANSWERED.contains(value)) {
					hits.add(new ConditionMatch(condition, version, question.questionId(), MatchKind.ANSWERED, null));
				}
				else if (UNANSWERED.contains(value)) {
					hits.add(new ConditionMatch(condition, version, question.questionId(), MatchKind.UNANSWERED, null));
				}
				else if (question.options().contains(value)) {
					hits.add(new ConditionMatch(condition, version, question.questionId(), MatchKind.OPTION, value));
				}
			}
			return hits;
		}
		return definition.questions()
			.stream()
			.filter(question -> question.isChoice() && question.options().contains(condition))
			.map(question -> new ConditionMatch(condition, version, question.questionId(), MatchKind.OPTION, condition))
			.toList();
	}

}
