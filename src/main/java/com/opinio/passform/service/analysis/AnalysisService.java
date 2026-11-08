package com.opinio.passform.service.analysis;

import java.util.ArrayList;
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

	private final PathAwareEvaluation pathAwareEvaluation = new PathAwareEvaluation();

	public AnalysisService(FormRepository forms, FormVersionRepository formVersions, ResponseRepository responses,
			QueryParser parser, ConditionMatcher matcher, RequestContextSchema contextSchema) {
		this.forms = forms;
		this.formVersions = formVersions;
		this.responses = responses;
		this.parser = parser;
		this.matcher = matcher;
		this.contextSchema = contextSchema;
	}

	public AnalyzeView analyze(AnalyzeCommand command) {
		// 본문을 읽기 전에 소유를 확인한다. 형식 오류 때문에 403과 400이 뒤바뀌지 않는다.
		Form form = ownedForm(command);
		JsonNode request = readRequest(command.body());
		// 통계 표현이 문항 문구·선택지인지 보려면 표 3행 전에 버전 정의가 필요하다. 판정은 하지 않고 읽기만 한다.
		List<Response> submitted = responses.findSubmittedNotDeletedByFormId(form.getId());
		Map<Integer, Map<String, Object>> definitions = respondedDefinitions(form.getId(), submitted);
		List<VersionQuestions> versions = definitions.entrySet()
			.stream()
			.map(entry -> VersionQuestions.of(entry.getKey(), entry.getValue()))
			.toList();

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
					: QueryText.reviveDroppedConditions(query, QueryText.reviveDroppedOptions(query, parsed.get(), versions));
		}
		else {
			context = RequestContextSchema.toContext(request.get("context"));
		}
		context = context.withoutUnusedKeys();

		Optional<ClarifyView> clarify = ClarifyRules.beforeMatching(query, context, aiSummaryEnabled(form), statistics,
				versions);
		if (clarify.isPresent()) {
			return AnalyzeView.clarify(context, clarify.get());
		}

		Set<Integer> versionNumbers = versions.stream().map(VersionQuestions::formVersion).collect(Collectors.toSet());
		List<ConditionMatch> matches = matcher.match(context.conditions(), versions)
			.stream()
			.filter(entry -> versionNumbers.contains(entry.formVersion()))
			.toList();

		List<ResponseRecord> records = submitted.stream().map(Response::toResponseRecord).toList();
		Map<Integer, FormDefinition> forms = new TreeMap<>();
		definitions.forEach((version, definition) -> forms.put(version, FormDefinition.from(definition)));
		ListSort sort = RequestContext.LIST.equals(context.operation()) && context.sort() != null
				? new ListSort(context.sort().by(), versions, forms, records) : null;

		clarify = ClarifyRules.afterMatching(context, matches, versions, sort);
		if (clarify.isPresent()) {
			return AnalyzeView.clarify(context, clarify.get());
		}
		failIfStepPending(context);
		return result(context, matches, versions, records, forms, sort);
	}

	private Form ownedForm(AnalyzeCommand command) {
		Form form = parseFormId(command.formId()).flatMap(forms::findById)
			.filter(found -> found.getDeletedAt() == null)
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (!form.getOwnerId().equals(command.userId())) {
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

	private static void failIfStepPending(RequestContext context) {
		if (RequestContext.SUMMARIZE.equals(context.operation())) {
			throw new AnalysisStepPendingException("요약(T7)");
		}
	}

	// 결과 처리(analyze_api.md 2절 ⑤). sort는 list에 sort가 있을 때만 있다.
	private AnalyzeView result(RequestContext context, List<ConditionMatch> matches, List<VersionQuestions> versions,
			List<ResponseRecord> records, Map<Integer, FormDefinition> forms, ListSort sort) {
		MatchMode mode = context.match() == null ? null : MatchMode.valueOf(context.match().toUpperCase(Locale.ROOT));
		EvaluationResult evaluation = pathAwareEvaluation.evaluate(context.conditions(), mode, matches, records, forms);
		List<String> responseIds = null;
		List<Integer> sortUnavailable = null;
		if (RequestContext.LIST.equals(context.operation())) {
			responseIds = evaluation.responseIds();
			if (sort != null) {
				String order = context.sort().order() == null ? "asc" : context.sort().order();
				sortUnavailable = sort.unavailableVersions(responseIds);
				responseIds = sort.sort(responseIds, order);
			}
		}
		return AnalyzeView.ok(context, matchedViews(evaluation.matched(), versions),
				CoverageView.of(evaluation.coverage(), sortUnavailable),
				new ResultView(context.operation(), evaluation.count(), responseIds, null));
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

}
