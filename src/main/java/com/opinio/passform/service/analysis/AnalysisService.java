package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.MatchMode;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalyzeView.ClarifyView;
import com.opinio.passform.service.analysis.AnalyzeView.CoverageView;
import com.opinio.passform.service.analysis.AnalyzeView.MatchedView;
import com.opinio.passform.service.analysis.AnalyzeView.ResultView;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// POST /forms/{form_id}/analyze (SPEC 5절 235·246~349행). 처리 순서는 코드가 고정한다(docs/api/analyze_api.md 2절).
// LLM을 부르는 동안 DB 연결을 잡지 않도록 메서드에 트랜잭션을 걸지 않는다. 읽는 값은 모두 즉시 로딩되는 열이다.
@Service
public class AnalysisService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final FormRepository forms;

	private final FormVersionRepository formVersions;

	private final ResponseRepository responses;

	private final QueryParser parser;

	private final ConditionMatcher matcher;

	private final RequestContextSchema contextSchema;

	private final SummaryService summaries;

	private final PathAwareEvaluation pathAwareEvaluation = new PathAwareEvaluation();

	public AnalysisService(FormRepository forms, FormVersionRepository formVersions, ResponseRepository responses,
			QueryParser parser, ConditionMatcher matcher, RequestContextSchema contextSchema, SummaryService summaries) {
		this.forms = forms;
		this.formVersions = formVersions;
		this.responses = responses;
		this.parser = parser;
		this.matcher = matcher;
		this.contextSchema = contextSchema;
		this.summaries = summaries;
	}

	public AnalyzeView analyze(AnalyzeCommand command) {
		// 본문을 읽기 전에 소유를 확인한다. 형식 오류 때문에 403과 400이 뒤바뀌지 않는다.
		Form form = requireOwnedForm(command.userId(), command.formId());
		JsonNode request = readRequest(command.body());
		// 통계 표현이 문항 문구·선택지인지 보려면 표 3행 전에 버전 정의가 필요하다. 판정은 하지 않고 읽기만 한다.
		Candidates candidates = candidates(form);
		List<VersionQuestions> versions = candidates.versions();

		String query = null;
		boolean statistics = false;
		RequestContext context;
		if (request.hasNonNull("query")) {
			query = request.get("query").asString();
			if (QueryText.asksVersionScope(query)) {
				return AnalyzeView.clarify(null, ClarifyRules.unsupportedScope());
			}
			Optional<RequestContext> parsed = parse(query);
			if (parsed.isEmpty()) {
				return AnalyzeView.clarify(null, ClarifyRules.parseFailed());
			}
			statistics = QueryText.asksStatistics(query, versions);
			// 스키마 재검증 뒤, 표 3행 전에 보정한다(SPEC 334행). 조건이 있으면 선택지 문구를, 없으면 남는 낱말을 되살린다.
			context = statistics ? parsed.get()
					: QueryText.reviveDroppedConditions(query, QueryText.reviveDroppedOptions(query, parsed.get(), versions),
							versions);
		}
		else {
			context = RequestContextSchema.toContext(request.get("context"));
		}
		Evaluated evaluated = evaluate(form, query, statistics, context, candidates);
		return evaluated.clarify() != null ? evaluated.clarify() : result(evaluated, candidates);
	}

	// 내보내기의 context 필터(AC49). analyze와 같은 되묻기·매칭·평가·정렬을 쓰고, 요약 경로는 부르지 않는다.
	// 행 순서: list는 분석의 response_ids 순서, 그 밖에는 저장소 순서(버전 → 제출 시각 → ID)다(SPEC 243행).
	public ExportSelection selectForExport(Form form, JsonNode context) {
		if (!contextSchema.errors(context).isEmpty()) {
			throw invalidContext();
		}
		Candidates candidates = candidates(form);
		Evaluated evaluated = evaluate(form, null, false, RequestContextSchema.toContext(context), candidates);
		if (evaluated.clarify() != null) {
			return new ExportSelection(evaluated.clarify().clarify(), List.of());
		}
		Map<String, Response> byId = candidates.submitted()
			.stream()
			.collect(Collectors.toMap(response -> response.getId().toString(), response -> response));
		if (RequestContext.LIST.equals(evaluated.context().operation())) {
			return new ExportSelection(null, listOrder(evaluated).stream().map(byId::get).toList());
		}
		Set<String> matchedIds = Set.copyOf(evaluated.evaluation().responseIds());
		return new ExportSelection(null,
				candidates.submitted().stream().filter(r -> matchedIds.contains(r.getId().toString())).toList());
	}

	private Candidates candidates(Form form) {
		List<Response> submitted = responses.findSubmittedNotDeletedByFormId(form.getId());
		Map<Integer, Map<String, Object>> definitions = respondedDefinitions(form.getId(), submitted);
		List<VersionQuestions> versions = definitions.entrySet()
			.stream()
			.map(entry -> VersionQuestions.of(entry.getKey(), entry.getValue()))
			.toList();
		return new Candidates(submitted, definitions, versions);
	}

	// 표 3행부터 조건 평가까지다(analyze_api.md 2절). 되묻기면 clarify만 채워 돌려준다.
	private Evaluated evaluate(Form form, String query, boolean statistics, RequestContext requested,
			Candidates candidates) {
		RequestContext context = requested.withoutUnusedKeys();
		List<VersionQuestions> versions = candidates.versions();
		Optional<ClarifyView> clarify = ClarifyRules.beforeMatching(query, context, aiSummaryEnabled(form), statistics,
				versions);
		if (clarify.isPresent()) {
			return new Evaluated(AnalyzeView.clarify(context, clarify.get()), context, null, null);
		}

		Set<Integer> versionNumbers = versions.stream().map(VersionQuestions::formVersion).collect(Collectors.toSet());
		List<ConditionMatch> matches = matcher.match(context.conditions(), versions)
			.stream()
			.filter(entry -> versionNumbers.contains(entry.formVersion()))
			.toList();

		List<ResponseRecord> records = candidates.submitted().stream().map(Response::toResponseRecord).toList();
		Map<Integer, FormDefinition> definitions = new TreeMap<>();
		candidates.definitions().forEach((version, definition) -> definitions.put(version, FormDefinition.from(definition)));
		ListSort sort = RequestContext.LIST.equals(context.operation()) && context.sort() != null
				? new ListSort(context.sort().by(), versions, definitions, records) : null;

		clarify = ClarifyRules.afterMatching(context, matches, versions, sort);
		if (clarify.isPresent()) {
			return new Evaluated(AnalyzeView.clarify(context, clarify.get()), context, null, null);
		}
		MatchMode mode = context.match() == null ? null : MatchMode.valueOf(context.match().toUpperCase(Locale.ROOT));
		return new Evaluated(null, context, pathAwareEvaluation.evaluate(context.conditions(), mode, matches, records, definitions),
				sort);
	}

	// 없거나 삭제된 폼은 404, 남의 폼은 403이다.
	public Form requireOwnedForm(UUID userId, String formId) {
		Form form = parseFormId(formId).flatMap(forms::findById)
			.filter(found -> found.getDeletedAt() == null)
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (!form.getOwnerId().equals(userId)) {
			throw new DomainException(ErrorCode.FORBIDDEN);
		}
		return form;
	}

	private static Optional<UUID> parseFormId(String formId) {
		try {
			return Optional.of(UUID.fromString(formId));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	// {query}와 {context} 중 정확히 하나만 받는다(SPEC 235행). context는 스키마까지 검사한다(AC32).
	private JsonNode readRequest(String body) {
		JsonNode request = readJson(body).orElseThrow(AnalysisService::invalidContext);
		if (!request.isObject()) {
			throw invalidContext();
		}
		for (String key : request.propertyNames()) {
			if (!"query".equals(key) && !"context".equals(key)) {
				throw invalidContext();
			}
		}
		boolean hasQuery = request.hasNonNull("query");
		boolean hasContext = request.hasNonNull("context");
		if (hasQuery == hasContext) {
			throw invalidContext();
		}
		if (hasQuery && (!request.get("query").isString() || request.get("query").asString().isBlank())) {
			throw invalidContext();
		}
		if (hasContext && !contextSchema.errors(request.get("context")).isEmpty()) {
			throw invalidContext();
		}
		return request;
	}

	// 형식 오류에만 1회 다시 부른다(AC30). operation null은 스키마에 맞으므로 여기서 걸리지 않는다.
	private Optional<RequestContext> parse(String query) {
		String previousError = null;
		for (int attempt = 0; attempt < 2; attempt++) {
			String output = parser.parse(query, previousError);
			Optional<JsonNode> json = readJson(output);
			List<String> errors = json.isEmpty() ? List.of("JSON 객체가 아니다") : contextSchema.errors(json.get());
			if (errors.isEmpty()) {
				return json.map(RequestContextSchema::toContext);
			}
			previousError = String.join("; ", errors);
		}
		return Optional.empty();
	}

	private static Optional<JsonNode> readJson(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(JSON.readTree(text));
		}
		catch (JacksonException e) {
			return Optional.empty();
		}
	}

	private static DomainException invalidContext() {
		return new DomainException(ErrorCode.INVALID_CONTEXT);
	}

	// 첫 배포 뒤 잠기므로 최신 배포 정의에서 읽고, 배포 전이면 작업 정의에서 읽는다. 없으면 꺼짐이다(SPEC 157행).
	private boolean aiSummaryEnabled(Form form) {
		Map<String, Object> definition = form.getDefinitionVersion() == null ? form.getWorkingDefinition()
				: versionDefinition(form.getId(), form.getDefinitionVersion()).getDefinition();
		return definition != null && definition.get("settings") instanceof Map<?, ?> settings
				&& settings.get("ai_summary") instanceof Map<?, ?> aiSummary && Boolean.TRUE.equals(aiSummary.get("enabled"));
	}

	// 제출 응답이 있는 버전의 배포 정의. 버전 오름차순이다.
	private Map<Integer, Map<String, Object>> respondedDefinitions(UUID formId, List<Response> submitted) {
		Map<Integer, Map<String, Object>> definitions = new TreeMap<>();
		submitted.stream()
			.map(Response::getFormVersion)
			.distinct()
			.forEach(version -> definitions.put(version, versionDefinition(formId, version).getDefinition()));
		return definitions;
	}

	private FormVersion versionDefinition(UUID formId, int version) {
		return formVersions.findById(new FormVersionId(formId, version))
			.orElseThrow(() -> new IllegalStateException("배포 정의가 없다: 폼 버전 " + version));
	}

	// 결과 처리(analyze_api.md 2절 ⑤·⑥). sort는 list에 sort가 있을 때만 있다.
	private AnalyzeView result(Evaluated evaluated, Candidates candidates) {
		RequestContext context = evaluated.context();
		EvaluationResult evaluation = evaluated.evaluation();
		List<VersionQuestions> versions = candidates.versions();
		if (RequestContext.SUMMARIZE.equals(context.operation())) {
			return summaryResult(context, versions, candidates.submitted(), evaluation);
		}
		List<String> responseIds = null;
		List<Integer> sortUnavailable = null;
		if (RequestContext.LIST.equals(context.operation())) {
			responseIds = listOrder(evaluated);
			if (evaluated.sort() != null) {
				sortUnavailable = evaluated.sort().unavailableVersions(evaluation.responseIds());
			}
		}
		return AnalyzeView.ok(context, matchedViews(evaluation.matched(), versions),
				CoverageView.of(evaluation.coverage(), sortUnavailable),
				new ResultView(context.operation(), evaluation.count(), responseIds, null));
	}

	// list의 response_ids 순서다. analyze 출력과 내보내기 행이 같은 순서를 쓴다(AC49).
	private static List<String> listOrder(Evaluated evaluated) {
		List<String> responseIds = evaluated.evaluation().responseIds();
		if (evaluated.sort() == null) {
			return responseIds;
		}
		RequestContext.Sort sort = evaluated.context().sort();
		return evaluated.sort().sort(responseIds, sort.order() == null ? "asc" : sort.order());
	}

	// 요약 대상은 조건 평가가 정한 일치 응답이고, 위에서 한 번 읽은 응답에서 고른다(AC20, AC88).
	private AnalyzeView summaryResult(RequestContext context, List<VersionQuestions> versions, List<Response> submitted,
			EvaluationResult evaluation) {
		Set<String> matchedIds = Set.copyOf(evaluation.responseIds());
		List<Response> matched = submitted.stream()
			.filter(response -> matchedIds.contains(response.getId().toString()))
			.sorted(Comparator.comparing(response -> response.getId().toString()))
			.toList();
		SummaryService.Outcome outcome = summaries.summarize(context, versions, matched);
		return AnalyzeView.ok(context, matchedViews(evaluation.matched(), versions),
				CoverageView.of(evaluation.coverage().withSummaryUnavailable(outcome.unavailableVersions()), null),
				new ResultView(context.operation(), evaluation.count(), evaluation.responseIds(), outcome.summary()));
	}

	private static List<MatchedView> matchedViews(List<ConditionMatch> matched, List<VersionQuestions> versions) {
		Map<Integer, Map<String, Question>> lookup = ClarifyRules.lookup(versions);
		List<MatchedView> views = new ArrayList<>();
		for (ConditionMatch entry : matched) {
			Question question = ClarifyRules.question(lookup, entry.formVersion(), entry.questionId());
			views.add(new MatchedView(entry.condition(), entry.formVersion(), entry.questionId(), question.questionNote(),
					entry.kind().name().toLowerCase(Locale.ROOT), entry.option()));
		}
		return List.copyOf(views);
	}

	public record AnalyzeCommand(UUID userId, String formId, String body) {
	}

	// clarify가 있으면 파일을 만들지 않는다. responses는 내보낼 행 순서다.
	public record ExportSelection(ClarifyView clarify, List<Response> responses) {
	}

	private record Candidates(List<Response> submitted, Map<Integer, Map<String, Object>> definitions,
			List<VersionQuestions> versions) {
	}

	// clarify가 있으면 evaluation과 sort는 null이다. sort는 list에 sort가 있을 때만 있다.
	private record Evaluated(AnalyzeView clarify, RequestContext context, EvaluationResult evaluation, ListSort sort) {
	}

}
