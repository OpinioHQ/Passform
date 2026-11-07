package com.opinio.passform.domain.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.result.FormResults.Combined;
import com.opinio.passform.domain.result.FormResults.CombinedOption;
import com.opinio.passform.domain.result.FormResults.CombinedQuestion;
import com.opinio.passform.domain.result.FormResults.Version;
import com.opinio.passform.domain.result.FormResults.VersionQuestion;

// 결과 대시보드 통계(SPEC 333행, AC19). LLM 없이 코드로 센다(AGENTS 3절 3).
// 사람이 정한 것(2026-11-07): 선택형 3종만 넣는다, 0건 선택지도 넣는다, form_versions는 정의에 있는 버전이다,
// 제출 응답이 있는 버전만 넣는다, 순서는 낮은 버전부터 정의 순서로 처음 나온 순이다, 정의에 없는 답은 세지 않는다.
public final class ResultStatistics {

	private ResultStatistics() {
	}

	// responses는 submitted이면서 삭제되지 않은 응답이어야 한다(ResponseRepository.findSubmittedNotDeletedByFormId).
	// definitions는 응답이 있는 각 버전의 문항이다.
	public static FormResults calculate(Map<Integer, List<DefinitionQuestion>> definitions,
			List<ResponseRecord> responses) {
		Map<Integer, List<ResponseRecord>> byVersion = new TreeMap<>();
		for (ResponseRecord response : responses) {
			byVersion.computeIfAbsent(response.formVersion(), version -> new ArrayList<>()).add(response);
		}
		List<Version> versions = new ArrayList<>();
		Map<GroupKey, GroupBuilder> groups = new LinkedHashMap<>();
		for (Map.Entry<Integer, List<ResponseRecord>> entry : byVersion.entrySet()) {
			int version = entry.getKey();
			List<DefinitionQuestion> questions = definitions.get(version);
			if (questions == null) {
				throw new IllegalStateException("제출 응답이 있는 버전의 정의가 없다: " + version);
			}
			List<VersionQuestion> versionQuestions = new ArrayList<>();
			for (DefinitionQuestion question : questions) {
				if (!question.isChoice()) {
					continue;
				}
				Map<String, Integer> counts = count(question, entry.getValue());
				versionQuestions.add(new VersionQuestion(question.questionId(), question.questionNote(), counts));
				groups.computeIfAbsent(GroupKey.of(question), GroupBuilder::new).add(version, counts);
			}
			versions.add(new Version(version, entry.getValue().size(), versionQuestions));
		}
		List<CombinedQuestion> combined = groups.values().stream().map(GroupBuilder::build).toList();
		return new FormResults(responses.size(), new Combined(combined), versions);
	}

	// 선택지 문구로 센다. checkbox는 한 응답이 고른 선택지마다 1이다. 정의에 없는 문구는 세지 않는다.
	private static Map<String, Integer> count(DefinitionQuestion question, List<ResponseRecord> responses) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String option : question.options()) {
			counts.put(option, 0);
		}
		for (ResponseRecord response : responses) {
			Set<String> chosen = new LinkedHashSet<>(response.answers().getOrDefault(question.questionId(), List.of()));
			for (String value : chosen) {
				counts.computeIfPresent(value, (option, count) -> count + 1);
			}
		}
		return counts;
	}

	// 통합 보기의 묶기 키: question_id·문구·유형(AC19).
	private record GroupKey(String questionId, String questionNote, String questionType) {

		static GroupKey of(DefinitionQuestion question) {
			return new GroupKey(question.questionId(), question.questionNote(), question.questionType());
		}

	}

	private static final class GroupBuilder {

		private final GroupKey key;

		private final List<Integer> versions = new ArrayList<>();

		private final Map<String, OptionBuilder> options = new LinkedHashMap<>();

		GroupBuilder(GroupKey key) {
			this.key = key;
		}

		// 같은 선택지 문구만 합친다.
		void add(int version, Map<String, Integer> counts) {
			versions.add(version);
			counts.forEach((label, count) -> options.computeIfAbsent(label, OptionBuilder::new).add(version, count));
		}

		CombinedQuestion build() {
			List<CombinedOption> built = options.values().stream().map(OptionBuilder::build).toList();
			return new CombinedQuestion(key.questionId(), key.questionNote(), key.questionType(), versions, built);
		}

	}

	private static final class OptionBuilder {

		private final String label;

		private final List<Integer> versions = new ArrayList<>();

		private int count;

		OptionBuilder(String label) {
			this.label = label;
		}

		void add(int version, int versionCount) {
			versions.add(version);
			count += versionCount;
		}

		CombinedOption build() {
			return new CombinedOption(label, count, versions);
		}

	}

}
