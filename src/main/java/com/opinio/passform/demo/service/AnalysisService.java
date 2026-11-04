package com.opinio.passform.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opinio.passform.demo.domain.InvalidRequestException;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.demo.domain.analysis.ParsedContextRepair;
import com.opinio.passform.demo.domain.analysis.PathAwareEvaluator;
import com.opinio.passform.demo.domain.analysis.QueryGuard;
import com.opinio.passform.demo.domain.analysis.QuestionResolver;
import com.opinio.passform.demo.domain.analysis.RequestContext;
import com.opinio.passform.demo.domain.analysis.RequestContextValidator;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.demo.domain.analysis.ResponseSorter;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.Question;
import com.opinio.passform.demo.llm.QueryParser;
import com.opinio.passform.demo.service.AnalysisResponse.ChoiceQuestionOption;
import com.opinio.passform.demo.service.AnalysisResponse.CoverageView;
import com.opinio.passform.demo.service.AnalysisResponse.MatchedEvidence;
import com.opinio.passform.demo.service.AnalysisResponse.QuestionOption;
import com.opinio.passform.demo.service.AnalysisResponse.ResultView;
import com.opinio.passform.demo.service.AnalysisResponse.VersionCoverageView;

import tools.jackson.databind.JsonNode;

// 흐름: 범위 판정 → 파서 → 되묻기(3~5) → 매칭 → 되묻기(6~10) → 조건 평가 → 결과 처리 → 요약.
// 순서를 코드가 고정한 workflow다. 모델이 다음 단계를 정하지 않는다.
// LLM 호출이 길어 이 메서드에는 트랜잭션을 걸지 않는다. 읽기는 FormSnapshotLoader에서 끝낸다.
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private final FormSnapshotLoader loader;

	private final QueryParser parser;

	private final RequestContextValidator validator;

	private final ConditionMatchingService matching;

	private final SummaryService summaries;

	private final PathAwareEvaluator evaluator = new PathAwareEvaluator();

	public AnalysisService(FormSnapshotLoader loader, QueryParser parser, RequestContextValidator validator,
			ConditionMatchingService matching, SummaryService summaries) {
		this.loader = loader;
		this.parser = parser;
		this.validator = validator;
		this.matching = matching;
		this.summaries = summaries;
	}

	public AnalysisResponse analyze(String formId, String userId, String query, JsonNode contextNode) {
		boolean hasQuery = query != null && !query.isBlank();
		if (hasQuery == (contextNode != null)) {
			throw new InvalidRequestException("invalid_context");
		}
		FormSnapshot snapshot = loader.loadOwned(formId, userId);
		long started = System.nanoTime();
		AnalysisResponse response = hasQuery ? fromQuery(snapshot, query) : fromContext(snapshot, contextNode);
		log.info("analyze form_id={} source={} status={} reason={} count={} elapsed_ms={}", formId,
				hasQuery ? "query" : "context", response.status(),
				(response.clarify() != null) ? response.clarify().reason() : null,
				(response.result() != null) ? response.result().count() : null,
				(System.nanoTime() - started) / 1_000_000);
		return response;
	}

	private AnalysisResponse fromQuery(FormSnapshot snapshot, String query) {
		List<String> formTexts = snapshot.formTexts();
		if (QueryGuard.mentionsUnsupportedScope(query, formTexts)) {
			return AnalysisResponse.clarify(null, "unsupported_scope",
					"AI 분석은 특정 버전만 따로 보거나 버전끼리 비교할 수 없어요. 모든 버전을 합쳐 분석하거나, 결과 화면의 버전별 보기를 이용해 주세요.",
					List.of("전체 버전 AI 분석", "결과 화면의 버전별 보기"));
		}
		boolean statistic = QueryGuard.mentionsUnsupportedStatistic(query, formTexts);
		Optional<RequestContext> parsed = parser.parse(query);
		if (parsed.isEmpty()) {
			return AnalysisResponse.clarify(null, "parse_failed", "요청을 이해하지 못했어요. 다른 말로 다시 알려 주세요.", List.of());
		}
		RequestContext context = parsed.get().withoutIrrelevantFields();
		if (!statistic) {
			Map<Integer, FormDefinition> active = snapshot.activeDefinitions();
			context = ParsedContextRepair.repair(query, context, allOptions(active),
					expression -> active.values()
						.stream()
						.anyMatch(definition -> !QuestionResolver
							.resolve(expression, definition, snapshot.latestDefinition())
							.isEmpty()))
				.withoutIrrelevantFields();
		}
		return evaluate(snapshot, context, statistic, QueryGuard.mentionsNegation(query));
	}

	private AnalysisResponse fromContext(FormSnapshot snapshot, JsonNode contextNode) {
		if (!contextNode.isObject() || !validator.errors(contextNode).isEmpty()) {
			throw new InvalidRequestException("invalid_context");
		}
		RequestContext context = validator.toContext(contextNode).withoutIrrelevantFields();
		return evaluate(snapshot, context, false, false);
	}

	// SPEC 5절 clarify 표 3~10행 순서로 검사한다. 앞선 사유 하나만 돌려준다.
	private AnalysisResponse evaluate(FormSnapshot snapshot, RequestContext context, boolean statistic,
			boolean negationInQuery) {
		boolean aiSummary = snapshot.form().isAiSummaryEnabled();
		if (context.operation() == null || statistic) {
			String message = statistic
					? "평균·비율·그룹별 통계는 AI 분석에서 지원하지 않아요. 문항별 통계는 결과 화면에서 볼 수 있어요. 대신 인원 수, 목록, 요약 중 무엇을 볼까요?"
					: "인원 수, 목록, 요약 중 무엇을 볼까요? 문항별 통계는 결과 화면에서 볼 수 있어요.";
			return AnalysisResponse.clarify(context, "missing_operation", message,
					aiSummary ? List.of(RequestContext.COUNT, RequestContext.LIST, RequestContext.SUMMARIZE)
							: List.of(RequestContext.COUNT, RequestContext.LIST));
		}
		if (RequestContext.SUMMARIZE.equals(context.operation()) && !aiSummary) {
			return AnalysisResponse.clarify(context, "summary_disabled",
					"이 폼은 AI 요약을 켜지 않고 배포해서 요약할 수 없어요. 배포 후에는 바꿀 수 없으니 폼을 복사해 새로 정해 주세요.",
					List.of(RequestContext.COUNT, RequestContext.LIST));
		}
		if (context.conditions().size() >= 2 && context.match() == null) {
			return negationInQuery
					? AnalysisResponse.clarify(context, "missing_match",
							"긍정 조건과 부정 조건을 한 번에 처리할 수 없어요. 요청을 나누어 알려 주세요.", List.of())
					: AnalysisResponse.clarify(context, "missing_match", "조건을 모두 만족하는 응답을 볼까요, 하나라도 만족하는 응답을 볼까요?",
							List.of("all", "any"));
		}

		Map<Integer, FormDefinition> active = snapshot.activeDefinitions();
		ConditionMatchingService.Outcome matched = matching.match(context.conditions(), active,
				snapshot.latestDefinition(), snapshot.latestVersion());
		Resolution sortBy = (context.sort() != null) ? resolve(context.sort().by(), snapshot, active) : Resolution.NONE;
		Resolution target = (context.target() != null) ? resolve(context.target(), snapshot, active) : Resolution.NONE;

		if (!matched.unknownConditions().isEmpty()) {
			return AnalysisResponse.clarify(context, "unknown_condition",
					"\"" + String.join("\", \"", matched.unknownConditions())
							+ "\" 조건을 폼의 문항이나 선택지에서 찾지 못했어요. 아래 선택지 중에서 골라 주세요.",
					choiceQuestions(active));
		}
		if (context.sort() != null && sortBy.isEmpty()) {
			return AnalysisResponse.clarify(context, "unknown_condition",
					"\"" + context.sort().by() + "\" 문항을 폼에서 찾지 못했어요. 아래 문항 중에서 골라 주세요.", choiceQuestions(active));
		}
		if (context.target() != null && target.isEmpty()) {
			return AnalysisResponse.clarify(context, "unknown_condition",
					"\"" + context.target() + "\" 문항을 폼에서 찾지 못했어요. 아래 문항 중에서 골라 주세요.", summarizableQuestions(active));
		}
		if (!matched.ambiguous().isEmpty()) {
			String condition = matched.ambiguous().keySet().iterator().next();
			return AnalysisResponse.clarify(context, "ambiguous_match",
					"\"" + condition + "\" 표현이 뜻이 다른 여러 문항에 걸려요. 어느 문항을 뜻하는지 골라 주세요.",
					matched.ambiguous().get(condition).stream()
						.map(candidate -> new QuestionOption(candidate.formVersion(), candidate.questionId(),
								candidate.questionNote()))
						.toList());
		}
		if ("all".equals(context.match()) && bindsTwoOptionsOfSingleChoice(matched.entries(), active)) {
			return AnalysisResponse.clarify(context, "ambiguous_match",
					"한 사람이 하나만 고를 수 있는 문항의 선택지를 함께 만족할 수는 없어요. 하나라도 해당하는 응답을 볼까요?", List.of("any"));
		}
		if (sortBy.isAmbiguous() || target.isAmbiguous()) {
			Resolution ambiguous = sortBy.isAmbiguous() ? sortBy : target;
			return AnalysisResponse.clarify(context, "ambiguous_match", "표현이 뜻이 다른 여러 문항에 걸려요. 어느 문항을 뜻하는지 골라 주세요.",
					ambiguous.candidates());
		}
		if ("not".equals(context.match())
				&& matched.entries().stream().anyMatch(entry -> entry.kind() == MatchKind.UNANSWERED)) {
			return AnalysisResponse.clarify(context, "double_negation", "미응답 조건에 \"아닌\"을 함께 쓸 수 없어요. 아래 요청이 맞나요?",
					List.of(String.join(", ", context.conditions()) + " 응답"));
		}

		EvaluationResult evaluation = evaluator.evaluate(context.conditions(), context.matchMode(), matched.entries(),
				snapshot.records(), snapshot.definitions());
		List<ResponseRecord> matchedRecords = snapshot.recordsOf(new HashSet<>(evaluation.responseIds()));

		List<Integer> sortUnavailable = null;
		List<String> responseIds = evaluation.responseIds();
		if (context.sort() != null) {
			Optional<ResponseSorter.Sorted> sorted = ResponseSorter.sort(matchedRecords, sortBy.single(),
					context.sort().order());
			if (sorted.isEmpty()) {
				return AnalysisResponse.clarify(context, "unsortable_field",
						"\"" + context.sort().by() + "\" 문항은 정렬할 수 없어요. 아래 문항 중에서 골라 주세요.",
						sortableQuestions(active, snapshot.records()));
			}
			responseIds = sorted.get().responseIds();
			sortUnavailable = sorted.get().sortUnavailableVersions().isEmpty() ? null
					: sorted.get().sortUnavailableVersions();
		}
		if (context.target() != null) {
			Optional<Question> blocked = target.single().values().stream()
				.filter(question -> !question.isSummarizable()).findFirst();
			if (blocked.isPresent()) {
				String message = blocked.get().isChoice()
						? "선택형 문항은 요약 대신 결과 화면의 통계로 볼 수 있어요. 아래 문항 중에서 골라 주세요."
						: "개인정보 문항은 외부로 보내지 않아 요약하지 않아요. 아래 문항 중에서 골라 주세요.";
				return AnalysisResponse.clarify(context, "unsummarizable_field", message,
						summarizableQuestions(active));
			}
		}
		return result(snapshot, context, evaluation, responseIds, sortUnavailable, target);
	}

	private AnalysisResponse result(FormSnapshot snapshot, RequestContext context, EvaluationResult evaluation,
			List<String> responseIds, List<Integer> sortUnavailable, Resolution target) {
		List<VersionCoverageView> byVersion = evaluation.coverage().notEvaluableByVersion().stream()
			.map(version -> new VersionCoverageView(version.formVersion(), version.responseCount(),
					version.unavailableConditions()))
			.toList();
		List<MatchedEvidence> matchedEvidence = evaluation.matched().stream()
			.map(entry -> toEvidence(entry, snapshot)).toList();
		AnalysisResponse.SummaryView summary = null;
		List<AnalysisResponse.VersionCount> summaryUnavailable = null;
		if (RequestContext.SUMMARIZE.equals(context.operation())) {
			SummaryService.Outcome outcome = summaries.summarize(context, snapshot,
					snapshot.responsesOf(new HashSet<>(evaluation.responseIds())),
					(context.target() != null) ? target.single() : null);
			summary = outcome.summary();
			summaryUnavailable = outcome.unavailableVersions().isEmpty() ? null : outcome.unavailableVersions();
		}
		CoverageView coverage = new CoverageView(evaluation.coverage().totalSubmittedCount(),
				evaluation.coverage().notEvaluableCount(), byVersion, sortUnavailable, summaryUnavailable);
		// 숫자는 조건 평가 결과에서만 온다. count에서는 ID 목록을 돌려주지 않는다(SPEC 5절 315행).
		List<String> ids = RequestContext.COUNT.equals(context.operation()) ? null : responseIds;
		return AnalysisResponse.ok(context, matchedEvidence, coverage,
				new ResultView(context.operation(), evaluation.count(), ids, summary));
	}

	private static MatchedEvidence toEvidence(ConditionMatch entry, FormSnapshot snapshot) {
		String note = snapshot.question(entry.formVersion(), entry.questionId()).map(Question::questionNote).orElse(null);
		return new MatchedEvidence(entry.condition(), entry.formVersion(), entry.questionId(), note,
				entry.kind().name().toLowerCase(Locale.ROOT), entry.option());
	}

	private static boolean bindsTwoOptionsOfSingleChoice(List<ConditionMatch> entries,
			Map<Integer, FormDefinition> active) {
		Map<String, Set<String>> optionsByQuestion = new HashMap<>();
		for (ConditionMatch entry : entries) {
			if (entry.kind() != MatchKind.OPTION) {
				continue;
			}
			boolean single = active.get(entry.formVersion()).question(entry.questionId())
				.map(Question::isSingleChoice).orElse(false);
			if (single) {
				optionsByQuestion.computeIfAbsent(entry.formVersion() + ":" + entry.questionId(), key -> new HashSet<>())
					.add(entry.option());
			}
		}
		return optionsByQuestion.values().stream().anyMatch(options -> options.size() > 1);
	}

	private static Resolution resolve(String expression, FormSnapshot snapshot, Map<Integer, FormDefinition> active) {
		Map<Integer, Question> single = new TreeMap<>();
		List<QuestionOption> candidates = new ArrayList<>();
		Set<String> questionIds = new HashSet<>();
		active.forEach((version, definition) -> {
			List<Question> found = QuestionResolver.resolve(expression, definition, snapshot.latestDefinition());
			found.forEach(question -> {
				questionIds.add(question.questionId());
				candidates.add(new QuestionOption(version, question.questionId(), question.questionNote()));
			});
			if (found.size() == 1) {
				single.put(version, found.get(0));
			}
		});
		return new Resolution(single, candidates, questionIds.size() > 1);
	}

	private static Set<String> allOptions(Map<Integer, FormDefinition> active) {
		Set<String> options = new HashSet<>();
		active.values().forEach(definition -> definition.questions()
			.forEach(question -> options.addAll(question.options())));
		return options;
	}

	private static List<ChoiceQuestionOption> choiceQuestions(Map<Integer, FormDefinition> active) {
		List<ChoiceQuestionOption> options = new ArrayList<>();
		active.forEach((version, definition) -> definition.questions().stream()
			.filter(Question::isChoice)
			.forEach(question -> options.add(new ChoiceQuestionOption(version, question.questionId(),
					question.questionNote(), question.options()))));
		return options;
	}

	private static List<QuestionOption> summarizableQuestions(Map<Integer, FormDefinition> active) {
		return questionsWhere(active, Question::isSummarizable);
	}

	private static List<QuestionOption> sortableQuestions(Map<Integer, FormDefinition> active,
			List<ResponseRecord> records) {
		return questionsWhere(active, question -> ResponseSorter.isSortable(question, records));
	}

	private static List<QuestionOption> questionsWhere(Map<Integer, FormDefinition> active,
			java.util.function.Predicate<Question> filter) {
		List<QuestionOption> options = new ArrayList<>();
		active.forEach((version, definition) -> definition.questions().stream()
			.filter(filter)
			.forEach(question -> options
				.add(new QuestionOption(version, question.questionId(), question.questionNote()))));
		return options;
	}

	private record Resolution(Map<Integer, Question> single, List<QuestionOption> candidates, boolean ambiguous) {

		static final Resolution NONE = new Resolution(new LinkedHashMap<>(), List.of(), false);

		boolean isEmpty() {
			return candidates.isEmpty();
		}

		boolean isAmbiguous() {
			return ambiguous;
		}

	}

}
