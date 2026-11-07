package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;
import com.opinio.passform.service.analysis.AnalyzeView.ClarifyView;
import com.opinio.passform.service.analysis.AnalyzeView.QuestionOption;

// SPEC 336~347행의 되묻기 표. 위에 있는 사유 하나만 돌려준다. LLM 없이 코드로 판정한다(AGENTS 3절 4항).
// 6·7행의 sort.by와 9행은 E7이 더한다. message는 화면에 그대로 나간다: 내부 ID와 "판정 불가"를 쓰지 않는다.
final class ClarifyRules {

	private ClarifyRules() {
	}

	static ClarifyView unsupportedScope() {
		return new ClarifyView("unsupported_scope",
				"특정 버전만 고르거나 버전끼리 비교하는 AI 분석은 지원하지 않아요. 전체 버전을 함께 분석하거나 결과 대시보드의 버전별 보기를 이용해 주세요.",
				List.of("전체 버전 AI 분석", "대시보드 버전별 보기"));
	}

	static ClarifyView parseFailed() {
		return new ClarifyView("parse_failed", "요청을 이해하지 못했어요. 다른 말로 다시 입력해 주세요.", List.of());
	}

	// 표 3~5행: 매칭 전에 판정한다. query는 {context}면 null이다.
	static Optional<ClarifyView> beforeMatching(String query, RequestContext context, boolean aiSummaryEnabled) {
		List<String> operations = aiSummaryEnabled ? List.of(RequestContext.COUNT, RequestContext.LIST, RequestContext.SUMMARIZE)
				: List.of(RequestContext.COUNT, RequestContext.LIST);
		if (query != null && QueryText.asksStatistics(query)) {
			return Optional.of(new ClarifyView("missing_operation",
					"평균·비율·그룹별 집계·문항별 통계는 AI 분석에서 지원하지 않아요. 통계는 결과 대시보드에서 볼 수 있어요. 인원 수, 응답 목록"
							+ (aiSummaryEnabled ? ", 요약" : "") + " 중 무엇을 볼까요?",
					operations));
		}
		if (context.operation() == null) {
			return Optional.of(new ClarifyView("missing_operation",
					"인원 수, 응답 목록" + (aiSummaryEnabled ? ", 요약" : "") + " 중 무엇을 볼까요? 문항별 통계는 결과 대시보드에서 볼 수 있어요.", operations));
		}
		if (RequestContext.SUMMARIZE.equals(context.operation()) && !aiSummaryEnabled) {
			return Optional.of(new ClarifyView("summary_disabled",
					"이 폼은 배포할 때 AI 요약을 켜지 않아 요약할 수 없어요. 배포 후에는 바꿀 수 없으니, 필요하면 폼을 복사해 새로 정해 주세요. 인원 수나 응답 목록은 볼 수 있어요.",
					List.of(RequestContext.COUNT, RequestContext.LIST)));
		}
		if (query != null && QueryText.mixesNegation(query, context)) {
			return Optional.of(new ClarifyView("missing_match",
					"'아닌' 조건과 다른 조건이 섞인 요청은 한 번에 처리할 수 없어요. 조건별로 나누어 물어봐 주세요.", List.of()));
		}
		if (context.conditions().size() >= 2 && context.match() == null) {
			return Optional.of(new ClarifyView("missing_match", "조건을 모두 만족하는 응답을 볼까요, 하나라도 만족하는 응답을 볼까요?",
					List.of("all", "any")));
		}
		return Optional.empty();
	}

	// 표 6·7·8·10행: 매칭 뒤에 판정한다. matches는 제출 응답이 있는 버전의 항목만 담는다.
	static Optional<ClarifyView> afterMatching(RequestContext context, List<ConditionMatch> matches,
			List<VersionQuestions> versions) {
		Map<Integer, Map<String, Question>> lookup = lookup(versions);
		List<QuestionOption> targetMatches = targetMatches(context, versions);
		return unknown(context, matches, versions, targetMatches)
			.or(() -> contradictorySingleChoice(context, matches, lookup))
			.or(() -> ambiguousCondition(context, matches, lookup))
			.or(() -> ambiguousTarget(targetMatches, lookup))
			.or(() -> doubleNegation(context, matches, lookup))
			.or(() -> unsummarizableTarget(context, targetMatches, versions, lookup));
	}

	static Map<Integer, Map<String, Question>> lookup(List<VersionQuestions> versions) {
		return versions.stream()
			.collect(Collectors.toMap(VersionQuestions::formVersion, version -> version.questions()
				.stream()
				.collect(Collectors.toMap(Question::questionId, question -> question))));
	}

	static Question question(Map<Integer, Map<String, Question>> lookup, int formVersion, String questionId) {
		Question question = lookup.getOrDefault(formVersion, Map.of()).get(questionId);
		if (question == null) {
			throw new IllegalStateException("매처가 정의에 없는 문항을 돌려줬다: 폼 버전 " + formVersion);
		}
		return question;
	}

	// 표 6행.
	private static Optional<ClarifyView> unknown(RequestContext context, List<ConditionMatch> matches,
			List<VersionQuestions> versions, List<QuestionOption> targetMatches) {
		Set<String> matched = matches.stream().map(ConditionMatch::condition).collect(Collectors.toSet());
		List<String> unknown = context.conditions().stream().filter(condition -> !matched.contains(condition)).toList();
		if (!unknown.isEmpty()) {
			return Optional.of(new ClarifyView("unknown_condition",
					"폼에서 '" + String.join("', '", unknown) + "'에 해당하는 문항이나 선택지를 찾지 못했어요. 아래 문항을 보고 다시 입력해 주세요.",
					questionOptions(versions, Question::isChoice)));
		}
		if (context.target() != null && targetMatches.isEmpty()) {
			return Optional.of(new ClarifyView("unknown_condition",
					"폼에서 '" + context.target() + "' 문항을 찾지 못했어요. 요약할 문항을 골라 주세요.", summarizableOptions(versions)));
		}
		return Optional.empty();
	}

	// 표 7행 전자: 한 버전의 단일 선택 문항에서 서로 다른 선택지를 all로 묶으면 항상 0건이다.
	private static Optional<ClarifyView> contradictorySingleChoice(RequestContext context, List<ConditionMatch> matches,
			Map<Integer, Map<String, Question>> lookup) {
		if (!"all".equals(context.match())) {
			return Optional.empty();
		}
		Map<String, Set<String>> optionsByQuestion = matches.stream()
			.filter(entry -> entry.kind() == MatchKind.OPTION)
			.filter(entry -> question(lookup, entry.formVersion(), entry.questionId()).isSingleChoice())
			.collect(Collectors.groupingBy(entry -> entry.formVersion() + "/" + entry.questionId(),
					Collectors.mapping(ConditionMatch::option, Collectors.toSet())));
		boolean contradictory = optionsByQuestion.values().stream().anyMatch(options -> options.size() >= 2);
		return contradictory ? Optional.of(new ClarifyView("ambiguous_match",
				"한 가지만 고를 수 있는 문항의 선택지를 모두 만족하는 응답은 없어요. 하나라도 해당하는 응답을 볼까요?", List.of("any"))) : Optional.empty();
	}

	// 표 7행 후자: 같은 표현이 뜻이 다른 문항에 걸리면 합치거나 고르지 않는다.
	// 버전 사이에 question_id만 다르고 문구·유형이 같으면 같은 문항이다(지웠다 다시 만든 문항). 한 버전 안의 두 문항은 항상 되묻는다.
	private static boolean differentMeanings(List<QuestionAt> questions) {
		boolean twoInOneVersion = questions.stream()
			.collect(Collectors.groupingBy(QuestionAt::formVersion,
					Collectors.mapping(at -> at.question().questionId(), Collectors.toSet())))
			.values()
			.stream()
			.anyMatch(ids -> ids.size() >= 2);
		boolean differentIds = questions.stream().map(at -> at.question().questionId()).distinct().count() >= 2;
		boolean differentWording = questions.stream()
			.map(at -> at.question().questionNote() + "\n" + at.question().questionType())
			.distinct()
			.count() >= 2;
		return twoInOneVersion || differentIds && differentWording;
	}

	private record QuestionAt(int formVersion, Question question) {
	}

	private static Optional<ClarifyView> ambiguousCondition(RequestContext context, List<ConditionMatch> matches,
			Map<Integer, Map<String, Question>> lookup) {
		for (String condition : context.conditions()) {
			List<ConditionMatch> entries = matches.stream().filter(entry -> entry.condition().equals(condition)).toList();
			List<QuestionAt> questions = entries.stream()
				.map(entry -> new QuestionAt(entry.formVersion(), question(lookup, entry.formVersion(), entry.questionId())))
				.toList();
			if (differentMeanings(questions)) {
				Set<QuestionOption> candidates = new LinkedHashSet<>();
				for (ConditionMatch entry : entries) {
					candidates.add(questionOption(entry.formVersion(), question(lookup, entry.formVersion(), entry.questionId())));
				}
				return Optional.of(new ClarifyView("ambiguous_match",
						"'" + condition + "'이(가) 뜻이 다른 여러 문항에 걸려 있어요. 어느 문항인지 골라 주세요.", List.copyOf(candidates)));
			}
		}
		return Optional.empty();
	}

	private static Optional<ClarifyView> ambiguousTarget(List<QuestionOption> targetMatches,
			Map<Integer, Map<String, Question>> lookup) {
		List<QuestionAt> questions = targetMatches.stream()
			.map(match -> new QuestionAt(match.formVersion(), question(lookup, match.formVersion(), match.questionId())))
			.toList();
		if (differentMeanings(questions)) {
			return Optional.of(new ClarifyView("ambiguous_match", "요약할 문항이 여러 개에 걸려 있어요. 어느 문항인지 골라 주세요.", targetMatches));
		}
		return Optional.empty();
	}

	// 표 8행. 코드가 혼자 not을 버리지 않고 확정 문장을 확인받는다(AC29).
	private static Optional<ClarifyView> doubleNegation(RequestContext context, List<ConditionMatch> matches,
			Map<Integer, Map<String, Question>> lookup) {
		if (!"not".equals(context.match())
				|| matches.stream().noneMatch(entry -> entry.kind() == MatchKind.UNANSWERED)) {
			return Optional.empty();
		}
		List<String> labels = new ArrayList<>();
		for (String condition : context.conditions()) {
			labels.add(matches.stream()
				.filter(entry -> entry.condition().equals(condition) && entry.kind() == MatchKind.UNANSWERED)
				.findFirst()
				.map(entry -> question(lookup, entry.formVersion(), entry.questionId()).questionNote() + " 미응답자")
				.orElse(condition));
		}
		String suffix = switch (context.operation()) {
			case RequestContext.LIST -> " 목록";
			case RequestContext.SUMMARIZE -> " 요약";
			default -> " 수";
		};
		return Optional.of(new ClarifyView("double_negation", "미응답 조건과 '아닌'이 함께 있어요. 이 요청이 맞나요?",
				List.of(String.join(", ", labels) + suffix)));
	}

	// 표 10행.
	private static Optional<ClarifyView> unsummarizableTarget(RequestContext context, List<QuestionOption> targetMatches,
			List<VersionQuestions> versions, Map<Integer, Map<String, Question>> lookup) {
		List<Question> questions = targetMatches.stream()
			.map(match -> question(lookup, match.formVersion(), match.questionId()))
			.toList();
		if (questions.stream().allMatch(Question::isSummarizable)) {
			return Optional.empty();
		}
		String message = questions.stream().anyMatch(Question::personal)
				? "'" + context.target() + "' 문항은 개인정보를 묻는 문항이라 외부로 보내지 않아 요약하지 않아요. 아래 문항 중에서 골라 주세요."
				: "'" + context.target() + "' 문항은 선택형이라 결과 대시보드의 통계에서 볼 수 있어요. 요약은 아래 문항 중에서 골라 주세요.";
		return Optional.of(new ClarifyView("unsummarizable_field", message, summarizableOptions(versions)));
	}

	// target은 문항 문구 완전 일치로 찾는다.
	private static List<QuestionOption> targetMatches(RequestContext context, List<VersionQuestions> versions) {
		if (context.target() == null) {
			return List.of();
		}
		return questionOptions(versions, question -> Objects.equals(question.questionNote(), context.target().strip()));
	}

	private static List<QuestionOption> summarizableOptions(List<VersionQuestions> versions) {
		return questionOptions(versions, Question::isSummarizable);
	}

	// 버전 오름차순, 버전 안에서 문항 순서다.
	private static List<QuestionOption> questionOptions(List<VersionQuestions> versions, Predicate<Question> filter) {
		List<QuestionOption> options = new ArrayList<>();
		for (VersionQuestions version : versions) {
			version.questions()
				.stream()
				.filter(filter)
				.forEach(question -> options.add(questionOption(version.formVersion(), question)));
		}
		return List.copyOf(options);
	}

	private static QuestionOption questionOption(int formVersion, Question question) {
		return new QuestionOption(formVersion, question.questionId(), question.questionNote(),
				question.isChoice() ? question.options() : null);
	}

}
