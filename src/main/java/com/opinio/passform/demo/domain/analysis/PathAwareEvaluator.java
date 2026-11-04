package com.opinio.passform.demo.domain.analysis;

import com.opinio.passform.domain.analysis.ConditionEvaluator;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.Coverage;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.MatchMode;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.VersionCoverage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.ResponsePathCalculator;

// 분기로 건너뛴 문항의 조건을 판정 불가로 만든다(SPEC 5절 313행). ConditionEvaluator의 시그니처를 바꾸지 않으려고
// 건너뛴 문항이 같은 응답끼리 묶어, 그 문항의 매칭을 뺀 채 평가한 뒤 합친다.
public final class PathAwareEvaluator {

	private final ConditionEvaluator evaluator = new ConditionEvaluator();

	public EvaluationResult evaluate(List<String> conditions, MatchMode match, List<ConditionMatch> matching,
			List<ResponseRecord> responses, Map<Integer, FormDefinition> definitions) {
		Map<Set<String>, List<ResponseRecord>> groups = new LinkedHashMap<>();
		for (ResponseRecord response : responses) {
			groups.computeIfAbsent(skippedConditionQuestions(response, matching, definitions), key -> new ArrayList<>())
				.add(response);
		}
		List<String> responseIds = new ArrayList<>();
		Map<Integer, Integer> counts = new TreeMap<>();
		Map<Integer, Set<String>> unavailable = new TreeMap<>();
		int notEvaluable = 0;
		for (Map.Entry<Set<String>, List<ResponseRecord>> group : groups.entrySet()) {
			List<ConditionMatch> reachable = matching.stream()
				.filter(entry -> !group.getKey().contains(entry.questionId()))
				.toList();
			EvaluationResult partial = evaluator.evaluate(conditions, match, reachable, group.getValue());
			responseIds.addAll(partial.responseIds());
			notEvaluable += partial.coverage().notEvaluableCount();
			for (VersionCoverage version : partial.coverage().notEvaluableByVersion()) {
				counts.merge(version.formVersion(), version.responseCount(), Integer::sum);
				unavailable.computeIfAbsent(version.formVersion(), key -> new LinkedHashSet<>())
					.addAll(version.unavailableConditions());
			}
		}
		responseIds.sort(Comparator.naturalOrder());
		List<VersionCoverage> byVersion = counts.entrySet().stream()
			.map(count -> new VersionCoverage(count.getKey(), count.getValue(),
					conditions.stream().filter(unavailable.get(count.getKey())::contains).toList()))
			.toList();
		return new EvaluationResult(responseIds.size(), List.copyOf(responseIds), matchedEvidence(matching, responses),
				new Coverage(responses.size(), notEvaluable, byVersion));
	}

	private static Set<String> skippedConditionQuestions(ResponseRecord response, List<ConditionMatch> matching,
			Map<Integer, FormDefinition> definitions) {
		Set<String> skipped = ResponsePathCalculator.skippedQuestionIds(definitions.get(response.formVersion()),
				response.answers());
		return matching.stream()
			.filter(entry -> entry.formVersion() == response.formVersion() && skipped.contains(entry.questionId()))
			.map(ConditionMatch::questionId)
			.collect(Collectors.toSet());
	}

	private static List<ConditionMatch> matchedEvidence(List<ConditionMatch> matching, List<ResponseRecord> responses) {
		Set<Integer> versions = responses.stream().map(ResponseRecord::formVersion).collect(Collectors.toSet());
		return matching.stream().filter(entry -> versions.contains(entry.formVersion())).toList();
	}

}
