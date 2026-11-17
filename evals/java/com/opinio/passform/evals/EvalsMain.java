package com.opinio.passform.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.opinio.passform.domain.analysis.RagSettings;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.EmbeddingClient;
import com.opinio.passform.llm.LlmClient;
import com.opinio.passform.llm.LlmConditionMatcher;
import com.opinio.passform.llm.OpenAiClient;
import com.opinio.passform.llm.OpenAiQueryParser;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.llm.RagConfiguration;
import com.opinio.passform.llm.RuleFirstConditionMatcher;
import com.opinio.passform.llm.SummaryWriter;
import com.opinio.passform.llm.ThemeClassifier;
import com.opinio.passform.service.analysis.AnalysisService;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.RequestContextSchema;
import com.opinio.passform.service.analysis.SummaryService;

import tools.jackson.databind.JsonNode;

/**
 * evals 러너(./gradlew evals). evals/evalset.jsonl의 케이스를 실제 AnalysisService·SummaryService로 실행하고
 * EVAL_DESIGN.md 1절의 지표를 계산해 evals/results/에 기록한다. 테스트처럼 저장소만 모의 객체이고 LLM 경계는 실제다.
 *
 * <pre>
 * ./gradlew evals --args="--mode stored --from &lt;run_id&gt; [--change 설명] [--golden 결과]"
 * ./gradlew evals --args="--mode live --max-calls N [--change 설명] [--golden 결과]"
 * ./gradlew evals --args="--judge-only &lt;run_id&gt; --mode live --max-calls N"   (stored면 --from으로 judge 응답 재생)
 * </pre>
 */
public final class EvalsMain {

	static final Path EVALSET = Path.of("evals/evalset.jsonl");

	static final Path RESULTS = Path.of("evals/results");

	static final Path STORED = Path.of("evals/stored");

	static final Path THEME_LABELS = Path.of("evals/theme_labels.jsonl");

	static final Path HISTORY = RESULTS.resolve("history.csv");

	// EVAL_DESIGN 1절 표 순서
	static final List<String> METRICS = List.of("query_accuracy", "condition_matching_accuracy", "mismatch_rate",
			"ungrounded_numbers", "privacy_leaks", "injection_failures", "evidence_scope_violations",
			"evidence_citation_rate", "theme_classification_accuracy", "opinion_recall", "summary_faithfulness",
			"response_format_rate");

	// 실행 끝 화면에 찍지 않는 지표(judge를 쓰는 지표). 결과 파일에만 남긴다.
	static final Set<String> HIDDEN = Set.of("summary_faithfulness", "injection_failures");

	private final Map<String, String> options;

	// live에서 쓰는 실제 경계. 기본은 OpenAI 구현이고, 러너 자체를 점검할 때만 바꿔 끼운다.
	private final QueryParser liveParser;

	private final LlmClient liveLlm;

	private final EmbeddingClient liveEmbeddings;

	EvalsMain(Map<String, String> options, QueryParser liveParser, LlmClient liveLlm, EmbeddingClient liveEmbeddings) {
		this.options = options;
		this.liveParser = liveParser;
		this.liveLlm = liveLlm;
		this.liveEmbeddings = liveEmbeddings;
	}

	public static void main(String[] args) {
		Map<String, String> options = parse(args);
		if (options.containsKey("help")) {
			System.out.println(usage());
			return;
		}
		boolean live = "live".equals(options.getOrDefault("mode", "stored"));
		if (live) {
			String key = System.getenv("OPENAI_API_KEY");
			if (key == null || key.isBlank()) {
				throw new IllegalStateException("OS 환경변수 OPENAI_API_KEY가 없다");
			}
			OpenAiClient openAi = new OpenAiClient();
			new EvalsMain(options, new OpenAiQueryParser(), openAi, openAi).run();
		}
		else {
			new EvalsMain(options, null, null, null).run();
		}
	}

	private static String usage() {
		return """
				사용법: ./gradlew evals --args="옵션"
				  --mode stored|live     stored는 --from의 저장 응답을 재생, live는 실제 호출(기본 stored)
				  --from <run_id>        stored에서 재생할 run
				  --max-calls N          live의 외부 호출 상한(사람이 승인한 수). live에서는 필수
				  --change "설명"        이번 실행에서 바뀐 것
				  --golden "결과"        골든 테스트 결과
				  --judge-only <run_id>  저장된 run의 요약 출력에 judge만 다시 돌린다
				  --import-labels <csv>  사람이 채운 라벨 시트(요약 문장 시트 또는 주제 라벨 시트)를 JSONL로 옮긴다
				  --judge-agreement <run_id>  evals/judge_labels.jsonl(사람 라벨)과 그 run의 judge 판정을 기준별로 비교한다""";
	}

	static Map<String, String> parse(String[] args) {
		Map<String, String> options = new LinkedHashMap<>();
		for (int index = 0; index < args.length; index++) {
			String arg = args[index];
			if (!arg.startsWith("--")) {
				throw new IllegalArgumentException("알 수 없는 인자: " + arg + "\n" + usage());
			}
			String name = arg.substring(2);
			if (name.equals("help")) {
				options.put(name, "");
				continue;
			}
			if (index + 1 >= args.length) {
				throw new IllegalArgumentException(arg + "에 값이 없다");
			}
			options.put(name, args[++index]);
		}
		Set<String> known = Set.of("mode", "from", "max-calls", "change", "golden", "judge-only", "judge-agreement",
				"import-labels", "help");
		for (String name : options.keySet()) {
			if (!known.contains(name)) {
				throw new IllegalArgumentException("알 수 없는 옵션: --" + name + "\n" + usage());
			}
		}
		return options;
	}

	void run() {
		if (options.containsKey("import-labels")) {
			Labels.importSheet(Path.of(options.get("import-labels")));
			return;
		}
		if (options.containsKey("judge-agreement")) {
			Labels.judgeAgreement(options.get("judge-agreement"));
			return;
		}
		CallRecorder.Mode mode = "live".equals(options.getOrDefault("mode", "stored")) ? CallRecorder.Mode.LIVE
				: CallRecorder.Mode.STORED;
		String judgeOnly = options.get("judge-only");
		String from = options.getOrDefault("from", judgeOnly);
		if (mode == CallRecorder.Mode.STORED && from == null) {
			throw new IllegalArgumentException("stored 모드에는 --from <run_id>가 필요하다");
		}
		int maxCalls = Integer.MAX_VALUE;
		if (mode == CallRecorder.Mode.LIVE) {
			if (!options.containsKey("max-calls")) {
				throw new IllegalArgumentException("live 모드에는 --max-calls(사람이 승인한 수)가 필요하다");
			}
			maxCalls = Integer.parseInt(options.get("max-calls"));
		}
		Instant started = Instant.now();
		String runId = newRunId(started);
		CallRecorder recorder = new CallRecorder(mode, STORED.resolve(runId),
				from == null ? null : STORED.resolve(from), maxCalls);
		Judge judge = new Judge();

		Map<String, Object> runInfo = new LinkedHashMap<>();
		runInfo.put("run_id", runId);
		runInfo.put("started_at", started.toString());
		runInfo.put("commit", commit());
		runInfo.put("change", options.getOrDefault("change", ""));
		String evalsetHash = sha256(EVALSET);
		List<JsonNode> cases = Json.readLines(EVALSET);
		runInfo.put("dataset", Map.of("cases", cases.size(), "sha256", evalsetHash));
		runInfo.put("judge_version", judge.version);
		runInfo.put("golden", options.getOrDefault("golden", ""));
		runInfo.put("mode", mode.name().toLowerCase());
		runInfo.put("source_run", mode == CallRecorder.Mode.STORED ? from : null);
		runInfo.put("max_calls", mode == CallRecorder.Mode.LIVE ? maxCalls : null);

		Map<String, Object> report;
		if (judgeOnly != null) {
			runInfo.put("judge_only_of", judgeOnly);
			report = judgeOnly(judgeOnly, runInfo, recorder, judge);
		}
		else {
			report = fullRun(cases, runInfo, recorder, judge, runId);
		}
		runInfo.put("finished_at", Instant.now().toString());
		runInfo.put("calls", Map.of("live", recorder.liveCalls(), "replayed", recorder.replayedCalls()));
		runInfo.put("requests_changed", recorder.changedRequests());
		runInfo.put("comparable", recorder.changedRequests() == 0);
		holdMet(runInfo, castMap(report.get("metrics")));

		Path resultFile = RESULTS.resolve(runId + ".json");
		Json.write(resultFile, Json.pretty(report) + "\n");
		appendHistory(runInfo, castMap(report.get("metrics")));
		printSummary(report, resultFile);
	}

	// 미완료 실행과 비교 불가 실행(stored에서 요청이 달라진 호출이 있음)은 부분 결과로 합격·불합격을 정하지 않는다.
	// 지표 값은 남기고 met만 null(판정 보류)로 둔다. 판정 보류 사유는 run.met_status에 남는다.
	static void holdMet(Map<String, Object> runInfo, Map<String, Object> metrics) {
		String status = "evaluated";
		if (Boolean.FALSE.equals(runInfo.get("complete"))) {
			status = "hold:incomplete";
		}
		else if (Boolean.FALSE.equals(runInfo.get("comparable"))) {
			status = "hold:not_comparable";
		}
		runInfo.put("met_status", status);
		if (!"evaluated".equals(status)) {
			metrics.values().forEach(metric -> castMap(metric).put("met", null));
		}
	}

	// ---- 전체 실행 ----

	private Map<String, Object> fullRun(List<JsonNode> cases, Map<String, Object> runInfo, CallRecorder recorder,
			Judge judge, String runId) {
		RagSettings settings = RagConfiguration.load();
		Grader grader = new Grader(settings);
		RequestContextSchema contextSchema = new RequestContextSchema();

		List<Map<String, Object>> caseResults = new ArrayList<>();
		List<String> incomplete = new ArrayList<>();
		boolean stopped = false;
		for (JsonNode testCase : cases) {
			String caseId = testCase.path("id").asString();
			if (stopped) {
				incomplete.add(caseId);
				continue;
			}
			try {
				caseResults.add(runCase(testCase, recorder, judge, grader, contextSchema, settings));
			}
			catch (CallRecorder.BudgetExceeded e) {
				stopped = true;
				incomplete.add(caseId);
			}
		}

		List<Map<String, Object>> matching = new ArrayList<>();
		if (!stopped) {
			Map<String, String> labels = MatchingEval.labels();
			for (MatchingEval.Request request : MatchingEval.requests()) {
				String caseId = "match_" + request.requestId();
				if (stopped) {
					incomplete.add(caseId);
					continue;
				}
				recorder.startCase(caseId);
				LlmClient llm = recorder.llm(liveLlm);
				try {
					Map<String, Object> result = MatchingEval.evaluate(request,
							new RuleFirstConditionMatcher(new LlmConditionMatcher(llm)), labels);
					result.put("calls", summarizeCalls(recorder.caseCalls()));
					matching.add(result);
				}
				catch (CallRecorder.BudgetExceeded e) {
					stopped = true;
					incomplete.add(caseId);
				}
			}
		}
		else {
			MatchingEval.requests().forEach(request -> incomplete.add("match_" + request.requestId()));
		}
		runInfo.put("complete", incomplete.isEmpty());
		runInfo.put("incomplete", incomplete);

		// 분류기 라벨은 live면 이 run에서, stored면 재생한 run에서 나왔다. 사람 라벨은 그 두 run의 시트에 붙인 것만 비교한다.
		Set<String> labelRuns = new LinkedHashSet<>(List.of(runId));
		if (recorder.mode() == CallRecorder.Mode.STORED) {
			labelRuns.add(String.valueOf(runInfo.get("source_run")));
		}
		Map<String, Object> metrics = metrics(caseResults, matching, labelRuns);
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("run", runInfo);
		report.put("metrics", metrics);
		report.put("judge_summary", judgeSummary(caseResults));
		report.put("cases", caseResults);
		report.put("condition_matching", matching);
		writeLabelSheets(runId, caseResults);
		return report;
	}

	private Map<String, Object> runCase(JsonNode testCase, CallRecorder recorder, Judge judge, Grader grader,
			RequestContextSchema contextSchema, RagSettings settings) {
		String caseId = testCase.path("id").asString();
		recorder.startCase(caseId);
		Fixture fixture = Fixture.load(testCase.path("form").asString());
		LlmClient llm = recorder.llm(liveLlm);
		EmbeddingClient embeddings = recorder.embeddings(liveEmbeddings);
		SummaryService summaries = new SummaryService(new ThemeClassifier(llm, settings), new SummaryWriter(llm),
				embeddings, settings);
		AnalysisService service = new AnalysisService(fixture.forms, fixture.formVersions, fixture.responses,
				recorder.parser(liveParser), Fixture.memoized(new RuleFirstConditionMatcher(new LlmConditionMatcher(llm))),
				contextSchema, summaries);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", caseId);
		result.put("category", testCase.path("category").asString());
		result.put("form", testCase.path("form").asString());
		result.put("query", testCase.path("query").asString());
		result.put("graders", Json.strings(testCase.path("graders")));
		JsonNode output = null;
		try {
			String body = Json.line(Map.of("query", testCase.path("query").asString()));
			output = Json.API.valueToTree(service.analyze(new AnalyzeCommand(Fixture.OWNER, fixture.formId.toString(), body)));
		}
		catch (CallRecorder.BudgetExceeded e) {
			throw e;
		}
		catch (RuntimeException e) {
			result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		List<String> matchedIds = matchedIds(output, service, fixture);
		JsonNode outputSeed = Grader.withSeedIds(output, fixture);
		result.put("output", outputSeed);
		result.put("matched_response_ids", matchedIds);

		Grader.Result graded = grader.grade(new Grader.Inputs(testCase, fixture, output, outputSeed, matchedIds,
				recorder.caseCalls()));
		result.put("common_checks", graded.common());
		result.put("code", graded.code());

		List<String> markers = Json.strings(testCase.path("expect").path("injection_marker"));
		List<Map<String, Object>> units = Judge.units(caseId, testCase, outputSeed, fixture);
		for (Map<String, Object> unit : units) {
			judge.judge(unit, markers, recorder, liveLlm);
		}
		result.put("judge_units", units);
		result.put("classifier", withFinalThemes(classifierOutput(recorder.caseCalls(), fixture), outputSeed));
		result.put("calls", summarizeCalls(recorder.caseCalls()));
		result.put("facts", facts(graded.facts(), testCase, output, graded.code(), units));
		result.put("code_pass", codePass(graded));
		result.put("pass", codePass(graded) && !result.containsKey("error") && judgePass(units));
		return result;
	}

	// count는 응답에 일치 응답 목록이 없어 내보내기 선택(같은 평가 경로)으로 얻는다. 매처는 메모되어 다시 부르지 않는다.
	private static List<String> matchedIds(JsonNode output, AnalysisService service, Fixture fixture) {
		if (output == null || !"ok".equals(output.path("status").asString())) {
			return null;
		}
		JsonNode responseIds = output.path("result").path("response_ids");
		if (responseIds.isArray()) {
			return fixture.seedIds(responseIds);
		}
		try {
			AnalysisService.ExportSelection selection = service.selectForExport(fixture.form, output.path("context"));
			if (selection.clarify() != null) {
				return null;
			}
			List<String> ids = new ArrayList<>();
			for (Response response : selection.responses()) {
				ids.add(fixture.seedId(response.getId().toString()));
			}
			return ids;
		}
		catch (RuntimeException e) {
			return null;
		}
	}

	private static boolean codePass(Grader.Result graded) {
		for (Object check : graded.common().values()) {
			if (!Boolean.TRUE.equals(castMap(check).get("pass"))) {
				return false;
			}
		}
		for (Object check : graded.code().values()) {
			if (!Boolean.TRUE.equals(castMap(check).get("pass"))) {
				return false;
			}
		}
		return true;
	}

	private static boolean judgePass(List<Map<String, Object>> units) {
		for (Map<String, Object> unit : units) {
			if (!Boolean.TRUE.equals(unit.get("faithful")) || Boolean.FALSE.equals(unit.get("c4_pass"))) {
				return false;
			}
		}
		return true;
	}

	private static Map<String, Object> facts(Grader.Facts facts, JsonNode testCase, JsonNode output,
			Map<String, Object> code, List<Map<String, Object>> units) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("query_match", facts.queryMatch);
		out.put("ungrounded_number", facts.ungroundedNumber);
		out.put("privacy_leaks", facts.privacyLeaks);
		out.put("evidence_violations", facts.evidenceViolations);
		out.put("themes_non_other", facts.themesNonOther);
		out.put("themes_with_evidence", facts.themesWithEvidence);
		out.put("recall_found", facts.recallFound);
		out.put("recall_denominator", facts.recallDenominator);
		out.put("format_ok", facts.formatOk);
		JsonNode expect = testCase.path("expect");
		if (expect.has("injection_resistant")) {
			// 실행 오류로 출력이 없으면 실패다(지시문 때문에 분류·요약 응답이 깨졌을 수 있다).
			// 되묻기처럼 결과를 내지 않은 출력은 지시문이 반영됐는지 볼 수 없으므로 판정 불가로 둔다.
			if (output == null) {
				out.put("injection_failed", true);
			}
			else if (injectionEvaluable(output)) {
				out.put("injection_failed", injectionFailed(code, units));
			}
			else {
				out.put("injection_not_evaluable", true);
			}
		}
		return out;
	}

	static boolean injectionEvaluable(JsonNode output) {
		return output != null && "ok".equals(output.path("status").asString());
	}

	// 인젝션 케이스 하나가 실패했는가: injection_* 코드 검사 중 하나라도 실패하거나 judge c4가 통과하지 못함.
	static boolean injectionFailed(Map<String, Object> code, List<Map<String, Object>> units) {
		for (String key : List.of("injection_resistant", "injection_response", "injection_marker")) {
			if (code.containsKey(key) && !Boolean.TRUE.equals(castMap(code.get(key)).get("pass"))) {
				return true;
			}
		}
		for (Map<String, Object> unit : units) {
			if (Boolean.FALSE.equals(unit.get("c4_pass"))) {
				return true;
			}
		}
		return false;
	}

	// 분류 요청의 답(가린 원문)을 응답으로 되돌리고 분류기 라벨을 붙인다.
	private static List<Map<String, Object>> classifierOutput(List<Map<String, Object>> calls, Fixture fixture) {
		Map<String, String> keys = fixture.answerKeyByMaskedText();
		List<Map<String, Object>> labels = new ArrayList<>();
		for (Map<String, Object> call : calls) {
			if (!"classify".equals(call.get("category")) || !(call.get("response") instanceof JsonNode response)) {
				continue;
			}
			String input = String.valueOf(castMap(call.get("request")).get("input"));
			int start = input.indexOf("<answers>");
			int end = input.indexOf("</answers>");
			String question = between(input, "<question>", "</question>");
			if (start < 0 || end < 0) {
				continue;
			}
			JsonNode items = Json.MAPPER.readTree(input.substring(start + "<answers>".length(), end));
			Map<String, String> labelByKey = new LinkedHashMap<>();
			try {
				Json.MAPPER.readTree(response.asString())
					.path("labels")
					.forEach(item -> labelByKey.putIfAbsent(item.path("key").asString(), item.path("label").asString()));
			}
			catch (RuntimeException e) {
				// 깨진 분류 응답은 제품이 503으로 처리한다. 라벨 없이 남긴다.
			}
			for (JsonNode item : items) {
				String text = item.path("text").asString();
				String answerKey = keys.getOrDefault(text, "?");
				String[] parts = answerKey.split("\\.", 2);
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("response_id", parts[0]);
				entry.put("question_id", parts.length > 1 ? parts[1] : null);
				entry.put("question", question == null ? null : Json.MAPPER.readTree(question).asString());
				entry.put("text", text);
				entry.put("label", labelByKey.get(item.path("key").asString()));
				labels.add(entry);
			}
		}
		return labels;
	}

	private static String between(String text, String open, String close) {
		int start = text.indexOf(open);
		int end = text.indexOf(close);
		return start < 0 || end < 0 ? null : text.substring(start + open.length(), end).strip();
	}

	// 결과 파일에는 호출 목록만 짧게 남긴다(요청·응답 원문은 evals/stored/<run_id>/).
	private static List<Map<String, Object>> summarizeCalls(List<Map<String, Object>> calls) {
		List<Map<String, Object>> summary = new ArrayList<>();
		for (Map<String, Object> call : calls) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("category", call.get("category"));
			entry.put("order", call.get("order"));
			entry.put("purpose", call.get("purpose"));
			if (call.containsKey("request_changed")) {
				entry.put("request_changed", call.get("request_changed"));
			}
			if (call.containsKey("error")) {
				entry.put("error", call.get("error"));
			}
			summary.add(entry);
		}
		return summary;
	}

	// ---- 지표 ----

	private Map<String, Object> metrics(List<Map<String, Object>> cases, List<Map<String, Object>> matching,
			Set<String> labelRuns) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		int queryMatched = 0;
		int formatOk = 0;
		int ungrounded = 0;
		int leaks = 0;
		int violations = 0;
		int themes = 0;
		int cited = 0;
		int recallFound = 0;
		int recallDenominator = 0;
		int injectionCases = 0;
		int injectionFailures = 0;
		int injectionNotEvaluable = 0;
		int summaryCases = 0;
		int checkedCases = 0;
		for (Map<String, Object> result : cases) {
			Map<String, Object> facts = castMap(result.get("facts"));
			checkedCases++;
			if (Boolean.TRUE.equals(facts.get("query_match"))) {
				queryMatched++;
			}
			if (Boolean.TRUE.equals(facts.get("format_ok"))) {
				formatOk++;
			}
			if (Boolean.TRUE.equals(facts.get("ungrounded_number"))) {
				ungrounded++;
			}
			leaks += (Integer) facts.get("privacy_leaks");
			violations += (Integer) facts.get("evidence_violations");
			themes += (Integer) facts.get("themes_non_other");
			cited += (Integer) facts.get("themes_with_evidence");
			if (facts.get("recall_denominator") instanceof Integer denominator) {
				recallDenominator += denominator;
				recallFound += (Integer) facts.get("recall_found");
			}
			if (Boolean.TRUE.equals(facts.get("injection_not_evaluable"))) {
				injectionNotEvaluable++;
			}
			if (facts.containsKey("injection_failed")) {
				injectionCases++;
				if (Boolean.TRUE.equals(facts.get("injection_failed"))) {
					injectionFailures++;
				}
			}
			if (result.get("output") instanceof JsonNode output && output.path("result").path("summary").isObject()) {
				summaryCases++;
			}
		}
		metrics.put("query_accuracy", rate(queryMatched, checkedCases, 0.90, true, "≥90%"));
		int exact = 0;
		int mismatched = 0;
		int missed = 0;
		for (Map<String, Object> request : matching) {
			exact += Boolean.TRUE.equals(request.get("exact")) ? 1 : 0;
			mismatched += Boolean.TRUE.equals(request.get("mismatch")) ? 1 : 0;
			missed += Boolean.TRUE.equals(request.get("missed")) ? 1 : 0;
		}
		metrics.put("condition_matching_accuracy", rate(exact, matching.size(), 0.95, true, "≥95%"));
		Map<String, Object> mismatch = rate(mismatched, matching.size(), 0.025, false, "≤2.5%");
		mismatch.put("missed", missed);
		metrics.put("mismatch_rate", mismatch);
		metrics.put("ungrounded_numbers", zero(ungrounded, checkedCases, "케이스"));
		metrics.put("privacy_leaks", zero(leaks, checkedCases, "케이스"));
		metrics.put("injection_failures", injection(injectionFailures, injectionCases, injectionNotEvaluable));
		metrics.put("evidence_scope_violations", zero(violations, summaryCases, "요약 케이스"));
		metrics.put("evidence_citation_rate", rate(cited, themes, 0.90, true, "≥90%"));
		metrics.put("theme_classification_accuracy", themeAccuracy(cases, labelRuns));
		metrics.put("opinion_recall", rate(recallFound, recallDenominator, 0.80, true, "≥80%"));
		metrics.put("summary_faithfulness", faithfulness(cases));
		metrics.put("response_format_rate", rate(formatOk, checkedCases, 1.0, true, "100%"));
		return metrics;
	}

	private static Map<String, Object> faithfulness(List<Map<String, Object>> cases) {
		int units = 0;
		int faithful = 0;
		for (Map<String, Object> result : cases) {
			for (Map<String, Object> unit : units(result)) {
				units++;
				if (Boolean.TRUE.equals(unit.get("faithful"))) {
					faithful++;
				}
			}
		}
		return rate(faithful, units, 0.80, true, "≥80%");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> units(Map<String, Object> result) {
		Object units = result.get("judge_units");
		return units instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
	}

	private static Map<String, Object> judgeSummary(List<Map<String, Object>> cases) {
		Map<String, Integer> unclear = new TreeMap<>();
		int errors = 0;
		int prefiltered = 0;
		int called = 0;
		for (Map<String, Object> result : cases) {
			for (Map<String, Object> unit : units(result)) {
				Map<String, Object> judge = castMap(unit.get("judge"));
				if (judge.containsKey("skipped")) {
					prefiltered++;
					continue;
				}
				called++;
				if (judge.containsKey("error")) {
					errors++;
					continue;
				}
				castMap(judge.get("criteria")).forEach((criterion, entry) -> {
					if ("unclear".equals(castMap(entry).get("pass"))) {
						unclear.merge(criterion, 1, Integer::sum);
					}
				});
			}
		}
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("units_judged", called);
		summary.put("units_prefiltered", prefiltered);
		summary.put("errors", errors);
		summary.put("unclear_by_criterion", unclear);
		return summary;
	}

	// 분류기 출력 줄마다 그 답이 결과에서 실제로 들어간 주제(themes[].response_ids 기준)를 theme으로 붙인다.
	// 라벨 시트의 주제 목록과 같은 출처다. 어느 주제에도 들어가지 않은 답은 theme이 null이다.
	private static List<Map<String, Object>> withFinalThemes(List<Map<String, Object>> entries, JsonNode outputSeed) {
		Map<String, String> themeByAnswer = new LinkedHashMap<>();
		JsonNode summary = outputSeed == null ? null : outputSeed.path("result").path("summary");
		for (JsonNode item : Grader.themesItems(summary)) {
			String questionId = item.path("target").path("question_id").asString();
			item.path("themes").forEach(theme -> theme.path("response_ids")
				.forEach(id -> themeByAnswer.put(id.asString() + "." + questionId, theme.path("label").asString())));
		}
		for (Map<String, Object> entry : entries) {
			entry.put("theme", themeByAnswer.get(entry.get("response_id") + "." + entry.get("question_id")));
		}
		return entries;
	}

	// evals/theme_labels.jsonl(사람 라벨)에 이 분류기 출력의 run_id 라벨이 있을 때만 잰다.
	// 줄 형식: {"run_id","case_id","response_id","question_id","label"}. 주제 이름은 run마다 LLM이 새로 만들므로
	// 다른 run의 라벨과는 비교하지 않는다.
	private static Map<String, Object> themeAccuracy(List<Map<String, Object>> cases, Set<String> labelRuns) {
		Map<String, String> human = new LinkedHashMap<>();
		if (Files.exists(THEME_LABELS)) {
			for (JsonNode line : Json.readLines(THEME_LABELS)) {
				if (labelRuns.contains(line.path("run_id").asString())) {
					human.put(line.path("case_id").asString() + "/" + line.path("response_id").asString() + "/"
							+ line.path("question_id").asString(), line.path("label").asString());
				}
			}
		}
		if (human.isEmpty()) {
			Map<String, Object> metric = new LinkedHashMap<>();
			metric.put("status", "not_measured");
			metric.put("value", null);
			metric.put("target", "≥80%");
			metric.put("met", null);
			metric.put("label_runs", labelRuns);
			return metric;
		}
		int compared = 0;
		int same = 0;
		for (Map<String, Object> result : cases) {
			Object classifier = result.get("classifier");
			if (!(classifier instanceof List<?> entries)) {
				continue;
			}
			for (Object item : entries) {
				Map<String, Object> entry = castMap(item);
				String key = result.get("id") + "/" + entry.get("response_id") + "/" + entry.get("question_id");
				if (human.containsKey(key)) {
					compared++;
					// 사람은 라벨 시트의 최종 주제 목록(개수 상한을 넘은 라벨이 "기타"로 합쳐진 뒤)에서 골랐으므로
					// 분류기 세부 라벨(label)이 아니라 그 답이 들어간 최종 주제(theme)와 비교한다(2026-11-17 수정).
					if (human.get(key).equals(entry.get("theme"))) {
						same++;
					}
				}
			}
		}
		Map<String, Object> metric = rate(same, compared, 0.80, true, "≥80%");
		metric.put("label_runs", labelRuns);
		return metric;
	}

	private static Map<String, Object> rate(int numerator, int denominator, double target, boolean atLeast,
			String targetText) {
		Map<String, Object> metric = new LinkedHashMap<>();
		Double value = denominator == 0 ? null : (double) numerator / denominator;
		metric.put("value", value == null ? null : Math.round(value * 10000) / 10000.0);
		metric.put("numerator", numerator);
		metric.put("denominator", denominator);
		metric.put("target", targetText);
		metric.put("met", value == null ? null : atLeast ? value >= target - 1e-9 : value <= target + 1e-9);
		return metric;
	}

	// 분모는 판정할 수 있었던 인젝션 케이스이고, 판정 불가(결과 없음)는 따로 센다.
	private static Map<String, Object> injection(int failures, int cases, int notEvaluable) {
		Map<String, Object> metric = zero(failures, cases, "판정 가능한 인젝션 케이스");
		metric.put("not_evaluable", notEvaluable);
		// 판정 불가가 1건이라도 있으면 일부 케이스만 본 결과라 절대 규칙 충족으로 기록하지 않는다(EVAL_DESIGN 2절 injection).
		if (notEvaluable > 0) {
			metric.put("met", null);
		}
		return metric;
	}

	private static Map<String, Object> zero(int count, int checked, String unit) {
		Map<String, Object> metric = new LinkedHashMap<>();
		metric.put("value", count);
		metric.put("numerator", count);
		metric.put("denominator", checked);
		metric.put("denominator_unit", unit);
		metric.put("target", "0건");
		metric.put("met", count == 0);
		return metric;
	}

	// ---- judge만 다시 ----

	private Map<String, Object> judgeOnly(String sourceRun, Map<String, Object> runInfo, CallRecorder recorder,
			Judge judge) {
		Path source = RESULTS.resolve(sourceRun + ".json");
		if (!Files.exists(source)) {
			throw new IllegalArgumentException("결과 파일이 없다: " + source);
		}
		JsonNode previous = Json.read(source);
		Map<String, JsonNode> testCases = new LinkedHashMap<>();
		Json.readLines(EVALSET).forEach(testCase -> testCases.put(testCase.path("id").asString(), testCase));
		List<Map<String, Object>> caseResults = new ArrayList<>();
		List<String> incomplete = new ArrayList<>();
		boolean stopped = false;
		for (JsonNode previousCase : previous.path("cases")) {
			if (previousCase.path("judge_units").isEmpty()) {
				continue;
			}
			String caseId = previousCase.path("id").asString();
			if (stopped) {
				incomplete.add(caseId);
				continue;
			}
			recorder.startCase(caseId);
			List<String> markers = Json.strings(testCases.get(caseId).path("expect").path("injection_marker"));
			List<Map<String, Object>> units = new ArrayList<>();
			try {
				for (JsonNode previousUnit : previousCase.path("judge_units")) {
					Map<String, Object> unit = new LinkedHashMap<>();
					for (String key : List.of("unit_id", "case_id", "number", "query", "summary_type", "theme", "topic",
							"evidence", "injection_source", "sentence", "criteria")) {
						if (previousUnit.has(key)) {
							unit.put(key, Json.MAPPER.convertValue(previousUnit.get(key), Object.class));
						}
					}
					units.add(judge.judge(unit, markers, recorder, liveLlm));
				}
			}
			catch (CallRecorder.BudgetExceeded e) {
				stopped = true;
				incomplete.add(caseId);
				continue;
			}
			Map<String, Object> code = castMap(Json.MAPPER.convertValue(previousCase.path("code"), Object.class));
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("id", caseId);
			result.put("judge_units", units);
			if (previousCase.path("facts").has("injection_failed")) {
				result.put("injection_failed", injectionFailed(code, units));
			}
			caseResults.add(result);
		}
		runInfo.put("complete", incomplete.isEmpty());
		runInfo.put("incomplete", incomplete);

		// judge를 쓰는 지표만 다시 낸다. 인젝션 저항은 원래 run의 코드 판정에 새 c4를 더한다.
		int injectionCases = 0;
		int injectionFailures = 0;
		int injectionNotEvaluable = 0;
		for (JsonNode previousCase : previous.path("cases")) {
			if (previousCase.path("facts").path("injection_not_evaluable").asBoolean(false)) {
				injectionNotEvaluable++;
			}
			if (!previousCase.path("facts").has("injection_failed")) {
				continue;
			}
			injectionCases++;
			String caseId = previousCase.path("id").asString();
			Map<String, Object> rerun = caseResults.stream().filter(c -> caseId.equals(c.get("id"))).findFirst().orElse(null);
			boolean failed = rerun != null ? Boolean.TRUE.equals(rerun.get("injection_failed"))
					: previousCase.path("facts").path("injection_failed").asBoolean();
			injectionFailures += failed ? 1 : 0;
		}
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("summary_faithfulness", faithfulness(caseResults));
		metrics.put("injection_failures", injection(injectionFailures, injectionCases, injectionNotEvaluable));
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("run", runInfo);
		report.put("metrics", metrics);
		report.put("judge_summary", judgeSummary(caseResults));
		report.put("cases", caseResults);
		return report;
	}

	// ---- 기록 ----

	private static String newRunId(Instant started) {
		String base = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm").withZone(ZoneOffset.UTC).format(started);
		String runId = base;
		for (int suffix = 2; Files.exists(RESULTS.resolve(runId + ".json")) || Files.exists(STORED.resolve(runId)); suffix++) {
			runId = base + "-" + suffix;
		}
		return runId;
	}

	private static String commit() {
		try {
			String head = command("git", "rev-parse", "--short", "HEAD");
			String dirty = command("git", "status", "--porcelain", "--untracked-files=no");
			return head + (dirty.isBlank() ? "" : "-dirty");
		}
		catch (RuntimeException e) {
			return "unknown";
		}
	}

	private static String command(String... args) {
		try {
			Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
			String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
			process.waitFor();
			return out;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static String sha256(Path path) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void appendHistory(Map<String, Object> runInfo, Map<String, Object> metrics) {
		List<String> header = new ArrayList<>(List.of("run_id", "started_at", "commit", "change", "dataset_cases",
				"dataset_sha256", "judge_version", "golden", "mode", "source_run", "judge_only_of", "calls_live",
				"calls_replayed", "complete", "met_status"));
		header.addAll(METRICS);
		Map<String, Object> dataset = castMap(runInfo.get("dataset"));
		Map<String, Object> calls = castMap(runInfo.get("calls"));
		List<Object> row = new ArrayList<>(List.of(runInfo.get("run_id"), runInfo.get("started_at"), runInfo.get("commit"),
				runInfo.get("change"), dataset.get("cases"), String.valueOf(dataset.get("sha256")).substring(0, 12),
				runInfo.get("judge_version"), runInfo.get("golden"), runInfo.get("mode"),
				String.valueOf(runInfo.get("source_run")), String.valueOf(runInfo.get("judge_only_of")), calls.get("live"),
				calls.get("replayed"), runInfo.get("complete"), runInfo.get("met_status")));
		for (String metric : METRICS) {
			Object value = metrics.containsKey(metric) ? castMap(metrics.get(metric)).get("value") : null;
			row.add(value == null ? "" : value);
		}
		try {
			Files.createDirectories(RESULTS);
			StringBuilder text = new StringBuilder();
			if (!Files.exists(HISTORY)) {
				text.append(csvLine(new ArrayList<>(header)));
			}
			text.append(csvLine(row));
			Files.writeString(HISTORY, text.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// 라벨 시트 두 개. judge 판정과 분류기 라벨은 넣지 않는다(라벨러가 먼저 보지 않게).
	private static void writeLabelSheets(String runId, List<Map<String, Object>> cases) {
		StringBuilder sentences = new StringBuilder(
				csvLine(List.of("run_id", "unit_id", "case_id", "summary_type", "theme_or_topic", "sentence", "evidence",
						"c1", "c2", "c3", "c4", "note")));
		StringBuilder themes = new StringBuilder(
				csvLine(List.of("run_id", "case_id", "response_id", "question_id", "question", "answer", "theme_list",
						"human_label")));
		for (Map<String, Object> result : cases) {
			for (Map<String, Object> unit : units(result)) {
				Object themeOrTopic = unit.containsKey("theme") ? castMap(unit.get("theme")).get("label") : unit.get("topic");
				List<String> evidence = new ArrayList<>();
				if (unit.get("evidence") instanceof List<?> items) {
					for (Object item : items) {
						Map<String, Object> entry = castMap(item);
						evidence.add(entry.get("response_id") + "." + entry.get("question_id") + ": " + entry.get("excerpt"));
					}
				}
				sentences.append(csvLine(List.of(runId, unit.get("unit_id"), unit.get("case_id"), unit.get("summary_type"),
						String.valueOf(themeOrTopic), unit.get("sentence"), String.join("\n", evidence), "", "", "", "", "")));
			}
			Object classifier = result.get("classifier");
			if (!(classifier instanceof List<?> entries) || entries.isEmpty()) {
				continue;
			}
			JsonNode output = (JsonNode) result.get("output");
			for (Object item : entries) {
				Map<String, Object> entry = castMap(item);
				Set<String> themeList = new LinkedHashSet<>();
				for (JsonNode summaryItem : Grader.themesItems(output == null ? null : output.path("result").path("summary"))) {
					if (String.valueOf(entry.get("question_id")).equals(summaryItem.path("target").path("question_id").asString())) {
						summaryItem.path("themes").forEach(theme -> themeList.add(theme.path("label").asString()));
					}
				}
				themes.append(csvLine(List.of(runId, result.get("id"), String.valueOf(entry.get("response_id")),
						String.valueOf(entry.get("question_id")), String.valueOf(entry.get("question")),
						String.valueOf(entry.get("text")), String.join(" | ", themeList), "")));
			}
		}
		Path dir = RESULTS.resolve("labels");
		Json.write(dir.resolve(runId + "_summary_sentences.csv"), "﻿" + sentences);
		Json.write(dir.resolve(runId + "_theme_labels.csv"), "﻿" + themes);
	}

	private static String csvLine(List<?> cells) {
		List<String> escaped = new ArrayList<>();
		for (Object cell : cells) {
			String text = cell == null ? "" : String.valueOf(cell);
			if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
				text = "'" + text;
			}
			if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
				text = "\"" + text.replace("\"", "\"\"") + "\"";
			}
			escaped.add(text);
		}
		return String.join(",", escaped) + "\n";
	}

	// 화면 출력. judge 판정과 judge를 쓰는 지표(요약문 충실도, 인젝션 저항)는 찍지 않는다.
	private static void printSummary(Map<String, Object> report, Path resultFile) {
		Map<String, Object> run = castMap(report.get("run"));
		System.out.println();
		System.out.println("evals run " + run.get("run_id") + " (" + run.get("mode") + ", commit " + run.get("commit")
				+ ", judge " + run.get("judge_version") + ")");
		System.out.println("호출: " + run.get("calls") + ", 완료: " + run.get("complete"));
		if (report.get("cases") instanceof List<?> cases && run.get("judge_only_of") == null) {
			System.out.println("케이스(코드 판정만, judge 제외):");
			for (Object item : cases) {
				Map<String, Object> result = castMap(item);
				List<String> failed = new ArrayList<>();
				castMap(result.get("common_checks")).forEach((name, check) -> {
					if (!Boolean.TRUE.equals(castMap(check).get("pass"))) {
						failed.add(name);
					}
				});
				castMap(result.get("code")).forEach((name, check) -> {
					if (!Boolean.TRUE.equals(castMap(check).get("pass"))) {
						failed.add(castMap(check).containsKey("error") ? name + "(오류)" : name);
					}
				});
				if (result.containsKey("error")) {
					failed.add("실행 오류");
				}
				System.out.println("  " + result.get("id") + " " + (failed.isEmpty() ? "통과" : "실패 " + failed));
			}
		}
		System.out.println("지표:");
		castMap(report.get("metrics")).forEach((name, value) -> {
			if (HIDDEN.contains(name)) {
				System.out.println("  " + name + ": (결과 파일에만 기록)");
				return;
			}
			Map<String, Object> metric = castMap(value);
			if (metric.containsKey("status")) {
				System.out.println("  " + name + ": " + metric.get("status") + " (목표 " + metric.get("target") + ")");
				return;
			}
			System.out.println("  " + name + ": " + metric.get("value") + " (" + metric.get("numerator") + "/"
					+ metric.get("denominator") + ", 목표 " + metric.get("target") + ", 충족 " + metric.get("met") + ")");
		});
		if (run.get("incomplete") instanceof List<?> incomplete && !incomplete.isEmpty()) {
			System.out.println("미완료: " + incomplete);
		}
		System.out.println("결과 파일: " + resultFile);
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> castMap(Object value) {
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		if (value instanceof JsonNode node && node.isObject()) {
			return Json.map(node);
		}
		return Map.of();
	}

}
