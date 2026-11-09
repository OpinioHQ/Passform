package com.opinio.passform.domain.result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.opinio.passform.domain.result.FormResults.CombinedTextQuestion;
import com.opinio.passform.domain.result.FormResults.QuestionRef;
import com.opinio.passform.domain.result.FormResults.TextAnswer;
import com.opinio.passform.domain.result.FormResults.Version;
import com.opinio.passform.domain.result.FormResults.VersionQuestion;
import com.opinio.passform.domain.result.FormResults.VersionTextQuestion;

// 결과 대시보드 통계(SPEC 333행, AC19). LLM 없이 코드로 센다(AGENTS 3절 3).
// 사람이 정한 것(2026-11-07): 선택형 3종은 선택지별로 센다, 0건 선택지도 넣는다, form_versions는 정의에 있는 버전이다,
// 제출 응답이 있는 버전만 넣는다, 순서는 낮은 버전부터 정의 순서로 처음 나온 순이다, 정의에 없는 답은 세지 않는다.
// 사람이 정한 것(2026-11-09): 단답·장문은 세지 않고 답 목록(text_questions)으로 넣는다. 묶기 규칙은 선택형과 같고,
// 빈 답은 넣지 않으며, 답은 submitted_at 내림차순 → response_id 오름차순이다. question_order로 두 종류를 섞은 순서를 준다.
public final class ResultStatistics {

	private static final Set<String> TEXT_TYPES = Set.of("short_answer", "long_answer");

	private static final Comparator<TextAnswer> LATEST_FIRST = Comparator
		.comparing(TextAnswer::submittedAt, Comparator.nullsLast(Comparator.reverseOrder()))
		.thenComparing(TextAnswer::responseId);

	private ResultStatistics() {
	}

	// 제출 시각 없이 통계만 볼 때. 답의 submitted_at은 null이다.
	public static FormResults calculate(Map<Integer, List<DefinitionQuestion>> definitions,
			List<ResponseRecord> responses) {
		return calculate(definitions, responses, Map.of());
	}

	// responses는 submitted이면서 삭제되지 않은 응답이어야 한다(ResponseRepository.findSubmittedNotDeletedByFormId).
	// definitions는 응답이 있는 각 버전의 문항이고, submittedAt은 response_id별 제출 시각이다.
	public static FormResults calculate(Map<Integer, List<DefinitionQuestion>> definitions,
			List<ResponseRecord> responses, Map<String, Instant> submittedAt) {
		Map<Integer, List<ResponseRecord>> byVersion = new TreeMap<>();
		for (ResponseRecord response : responses) {
			byVersion.computeIfAbsent(response.formVersion(), version -> new ArrayList<>()).add(response);
		}
		List<Version> versions = new ArrayList<>();
		Map<GroupKey, GroupBuilder> groups = new LinkedHashMap<>();
		Map<GroupKey, TextGroupBuilder> textGroups = new LinkedHashMap<>();
		Set<GroupKey> order = new LinkedHashSet<>();
		for (Map.Entry<Integer, List<ResponseRecord>> entry : byVersion.entrySet()) {
			int version = entry.getKey();
			List<DefinitionQuestion> questions = definitions.get(version);
			if (questions == null) {
				throw new IllegalStateException("제출 응답이 있는 버전의 정의가 없다: " + version);
			}
			List<VersionQuestion> versionQuestions = new ArrayList<>();
			List<VersionTextQuestion> versionTexts = new ArrayList<>();
			List<QuestionRef> versionOrder = new ArrayList<>();
			for (DefinitionQuestion question : questions) {
				GroupKey key = GroupKey.of(question);
				if (question.isChoice()) {
					Map<String, Integer> counts = count(question, entry.getValue());
					versionQuestions.add(new VersionQuestion(question.questionId(), question.questionNote(), counts));
					groups.computeIfAbsent(key, GroupBuilder::new).add(version, counts);
				}
				else if (TEXT_TYPES.contains(question.questionType())) {
					List<TextAnswer> answers = answers(question, version, entry.getValue(), submittedAt);
					versionTexts.add(new VersionTextQuestion(question.questionId(), question.questionNote(),
							question.questionType(), answers));
					textGroups.computeIfAbsent(key, TextGroupBuilder::new).add(version, answers);
				}
				else {
					continue;
				}
				versionOrder.add(key.ref());
				order.add(key);
			}
			versions.add(new Version(version, entry.getValue().size(), versionQuestions, versionTexts, versionOrder));
		}
		List<CombinedQuestion> combined = groups.values().stream().map(GroupBuilder::build).toList();
		List<CombinedTextQuestion> combinedTexts = textGroups.values().stream().map(TextGroupBuilder::build).toList();
		List<QuestionRef> combinedOrder = order.stream().map(GroupKey::ref).toList();
		return new FormResults(responses.size(), new Combined(combined, combinedTexts, combinedOrder), versions);
	}

	// 그 버전 응답의 이 문항 답. 빈 답은 넣지 않는다.
	private static List<TextAnswer> answers(DefinitionQuestion question, int version, List<ResponseRecord> responses,
			Map<String, Instant> submittedAt) {
		List<TextAnswer> answers = new ArrayList<>();
		for (ResponseRecord response : responses) {
			String value = String.join("\n", response.answers().getOrDefault(question.questionId(), List.of()));
			if (!value.isBlank()) {
				answers.add(new TextAnswer(response.responseId(), version, value, submittedAt.get(response.responseId())));
			}
		}
		answers.sort(LATEST_FIRST);
		return answers;
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

		QuestionRef ref() {
			return new QuestionRef(questionId, questionNote, questionType);
		}

	}

	private static final class TextGroupBuilder {

		private final GroupKey key;

		private final List<Integer> versions = new ArrayList<>();

		private final List<TextAnswer> answers = new ArrayList<>();

		TextGroupBuilder(GroupKey key) {
			this.key = key;
		}

		void add(int version, List<TextAnswer> versionAnswers) {
			versions.add(version);
			answers.addAll(versionAnswers);
		}

		CombinedTextQuestion build() {
			List<TextAnswer> sorted = new ArrayList<>(answers);
			sorted.sort(LATEST_FIRST);
			return new CombinedTextQuestion(key.questionId(), key.questionNote(), key.questionType(), versions, sorted);
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
