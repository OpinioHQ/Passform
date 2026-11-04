package com.opinio.passform.domain.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

// SPEC 5절 313행: 제출 버전별 조건 참·거짓·판정 불가 판정과 all/any/not 결합 (match가 null이면 ALL)
public class ConditionEvaluator {

	public EvaluationResult evaluate(List<String> conditions, MatchMode match, List<ConditionMatch> matching,
			List<ResponseRecord> responses) {
		MatchMode mode = (match != null) ? match : MatchMode.ALL;
		List<ResponseRecord> submitted = responses.stream()
			.filter(response -> response.status() == ResponseStatus.SUBMITTED)
			.toList();
		// (조건, 버전)당 매칭은 하나다. 둘이면 toMap이 예외를 던져 어느 쪽으로도 세지 않는다.
		Map<ConditionVersion, ConditionMatch> matchIndex = matching.stream()
			.collect(Collectors.toMap(entry -> new ConditionVersion(entry.condition(), entry.formVersion()),
					Function.identity()));

		List<String> responseIds = new ArrayList<>();
		Map<Integer, Integer> notEvaluableCounts = new TreeMap<>();
		for (ResponseRecord response : submitted) {
			List<Verdict> verdicts = conditions.stream()
				.map(condition -> matchIndex.get(new ConditionVersion(condition, response.formVersion())))
				.map(entry -> evaluateCondition(entry, response))
				.toList();
			Verdict verdict = combine(verdicts, mode);
			if (verdict == Verdict.TRUE) {
				responseIds.add(response.responseId());
			}
			else if (verdict == Verdict.NOT_EVALUABLE) {
				notEvaluableCounts.merge(response.formVersion(), 1, Integer::sum);
			}
		}
		responseIds.sort(Comparator.naturalOrder());

		return new EvaluationResult(responseIds.size(), List.copyOf(responseIds), matchedEvidence(matching, submitted),
				coverage(conditions, matchIndex, submitted.size(), notEvaluableCounts));
	}

	// 경로상 비운 문항의 선택지 조건은 거짓이다(data/seed README 가정 14). 분기로 건너뛴 문항의 판정 불가는 이 함수 범위 밖이다.
	private static Verdict evaluateCondition(ConditionMatch entry, ResponseRecord response) {
		if (entry == null) {
			return Verdict.NOT_EVALUABLE;
		}
		List<String> values = response.answers().getOrDefault(entry.questionId(), List.of());
		boolean satisfied = switch (entry.kind()) {
			case OPTION -> values.contains(entry.option());
			case ANSWERED -> !values.isEmpty();
			case UNANSWERED -> values.isEmpty();
		};
		return satisfied ? Verdict.TRUE : Verdict.FALSE;
	}

	private static Verdict combine(List<Verdict> verdicts, MatchMode mode) {
		// 조건이 없으면 match와 무관하게 모든 제출 응답이 일치한다.
		if (verdicts.isEmpty()) {
			return Verdict.TRUE;
		}
		return switch (mode) {
			case ALL -> all(verdicts);
			case ANY -> any(verdicts);
			case NOT -> negate(any(verdicts));
		};
	}

	private static Verdict all(List<Verdict> verdicts) {
		if (verdicts.contains(Verdict.FALSE)) {
			return Verdict.FALSE;
		}
		return verdicts.contains(Verdict.NOT_EVALUABLE) ? Verdict.NOT_EVALUABLE : Verdict.TRUE;
	}

	private static Verdict any(List<Verdict> verdicts) {
		if (verdicts.contains(Verdict.TRUE)) {
			return Verdict.TRUE;
		}
		return verdicts.contains(Verdict.NOT_EVALUABLE) ? Verdict.NOT_EVALUABLE : Verdict.FALSE;
	}

	private static Verdict negate(Verdict verdict) {
		return switch (verdict) {
			case TRUE -> Verdict.FALSE;
			case FALSE -> Verdict.TRUE;
			case NOT_EVALUABLE -> Verdict.NOT_EVALUABLE;
		};
	}

	// 제출 응답이 있는 버전의 근거만 입력 순서대로 돌려준다(SPEC 5절 314행, AC23).
	private static List<ConditionMatch> matchedEvidence(List<ConditionMatch> matching, List<ResponseRecord> submitted) {
		Set<Integer> submittedVersions = submitted.stream()
			.map(ResponseRecord::formVersion)
			.collect(Collectors.toSet());
		return matching.stream().filter(entry -> submittedVersions.contains(entry.formVersion())).toList();
	}

	// 판정 불가 응답이 있는 버전만 버전 오름차순으로 담는다. 조건은 conditions 순서다.
	private static Coverage coverage(List<String> conditions, Map<ConditionVersion, ConditionMatch> matchIndex,
			int submittedCount, Map<Integer, Integer> notEvaluableCounts) {
		List<VersionCoverage> byVersion = notEvaluableCounts.entrySet()
			.stream()
			.map(count -> new VersionCoverage(count.getKey(), count.getValue(),
					conditions.stream()
						.filter(condition -> !matchIndex.containsKey(new ConditionVersion(condition, count.getKey())))
						.toList()))
			.toList();
		int notEvaluableCount = notEvaluableCounts.values().stream().mapToInt(Integer::intValue).sum();
		return new Coverage(submittedCount, notEvaluableCount, byVersion);
	}

	private enum Verdict {

		TRUE, FALSE, NOT_EVALUABLE

	}

	private record ConditionVersion(String condition, int formVersion) {
	}

}
