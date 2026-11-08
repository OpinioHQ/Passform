package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.opinio.passform.domain.analysis.ConditionEvaluator;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.Coverage;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.MatchMode;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.VersionCoverage;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.ResponsePathCalculator;

// 분기로 건너뛴 문항은 평가 함수 밖에서 처리한다(delegation_condition_evaluator.md §4, SPEC 5절 313행).
// 서버가 각 응답의 답으로 제출 당시 정의의 경로를 다시 계산하고, 경로 밖 문항에 걸린 매칭을 그 응답에서만 뺀다.
// 매칭이 없는 (조건, 버전)은 ConditionEvaluator가 판정 불가로 두므로, 건너뛴 응답만 판정 불가가 되고 경로 안에서 비운 응답은 거짓이다.
final class PathAwareEvaluation {

	private final ConditionEvaluator evaluator = new ConditionEvaluator();

	EvaluationResult evaluate(List<String> conditions, MatchMode mode, List<ConditionMatch> matches,
			List<ResponseRecord> records, Map<Integer, FormDefinition> definitions) {
		// 같은 버전·같은 건너뜀 문항 집합의 응답끼리 한 번에 평가한다.
		Map<Group, List<ResponseRecord>> groups = new LinkedHashMap<>();
		for (ResponseRecord record : records) {
			groups.computeIfAbsent(groupOf(record, matches, definitions), key -> new ArrayList<>()).add(record);
		}

		List<String> responseIds = new ArrayList<>();
		Map<Integer, Integer> notEvaluableCounts = new TreeMap<>();
		Map<Integer, Set<String>> unavailable = new TreeMap<>();
		for (Map.Entry<Group, List<ResponseRecord>> group : groups.entrySet()) {
			List<ConditionMatch> onPath = matches.stream().filter(entry -> !group.getKey().skips(entry)).toList();
			EvaluationResult part = evaluator.evaluate(conditions, mode, onPath, group.getValue());
			responseIds.addAll(part.responseIds());
			for (VersionCoverage version : part.coverage().notEvaluableByVersion()) {
				notEvaluableCounts.merge(version.formVersion(), version.responseCount(), Integer::sum);
				unavailable.computeIfAbsent(version.formVersion(), key -> new LinkedHashSet<>())
					.addAll(version.unavailableConditions());
			}
		}
		responseIds.sort(Comparator.naturalOrder());

		List<VersionCoverage> byVersion = notEvaluableCounts.entrySet()
			.stream()
			.map(count -> new VersionCoverage(count.getKey(), count.getValue(),
					conditions.stream().distinct().filter(unavailable.get(count.getKey())::contains).toList()))
			.toList();
		int notEvaluable = notEvaluableCounts.values().stream().mapToInt(Integer::intValue).sum();
		// matched는 경로와 무관하게 연결된 근거다. 화면은 이것으로 "분기로 건너뜀"과 "문항이 없던 버전"을 나눈다(analyze_api.md 5절).
		Set<Integer> respondedVersions = new TreeSet<>(records.stream().map(ResponseRecord::formVersion).toList());
		List<ConditionMatch> matched = matches.stream()
			.filter(entry -> respondedVersions.contains(entry.formVersion()))
			.toList();
		return new EvaluationResult(responseIds.size(), List.copyOf(responseIds), matched,
				new Coverage(records.size(), notEvaluable, byVersion));
	}

	private static Group groupOf(ResponseRecord record, List<ConditionMatch> matches,
			Map<Integer, FormDefinition> definitions) {
		FormDefinition definition = definitions.get(record.formVersion());
		Set<String> skipped = new TreeSet<>();
		ResponsePathCalculator.calculate(definition, record.answers())
			.skipped()
			.forEach(question -> skipped.add(question.questionId()));
		Set<String> relevant = new TreeSet<>();
		matches.stream()
			.filter(entry -> entry.formVersion() == record.formVersion() && skipped.contains(entry.questionId()))
			.forEach(entry -> relevant.add(entry.questionId()));
		return new Group(record.formVersion(), Set.copyOf(relevant));
	}

	private record Group(int formVersion, Set<String> skippedQuestionIds) {

		boolean skips(ConditionMatch entry) {
			return entry.formVersion() == formVersion && skippedQuestionIds.contains(entry.questionId());
		}

	}

}
