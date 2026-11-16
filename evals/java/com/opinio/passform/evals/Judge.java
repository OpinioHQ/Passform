package com.opinio.passform.evals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.opinio.passform.domain.form.VersionQuestions.Question;
import com.opinio.passform.llm.LlmClient;
import com.opinio.passform.llm.LlmRequest;

import tools.jackson.databind.JsonNode;

// 요약 문장 단위의 judge(EVAL_DESIGN 3절, evals/judge_prompt.md). 코드가 먼저 거른 문장은 judge를 부르지 않는다.
final class Judge {

	static final Path PROMPT_FILE = Path.of("evals/judge_prompt.md");

	private static final String MODEL = "gpt-6-luna";

	private static final Pattern PROMPT_BLOCK = Pattern
		.compile("(?ms)^<!-- prompt:start -->\\s*$(.*?)^<!-- prompt:end -->\\s*$");

	// 사람 수·건수를 뜻하는 숫자. 분·시간·일·개월 같은 단위는 잡지 않는다.
	private static final Pattern DIGIT_COUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(%|퍼센트|명|건|개(?!월))");

	private static final Pattern WORD_COUNT = Pattern
		.compile("(?<![\\p{L}])(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*(명|건|개(?!월))");

	private static final List<String> WORDS = List.of("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열");

	final String version;

	private final String prompt;

	Judge() {
		String file = Json.readString(PROMPT_FILE);
		Matcher matcher = PROMPT_BLOCK.matcher(file);
		if (!matcher.find()) {
			throw new IllegalStateException("judge 프롬프트 마커가 없다");
		}
		prompt = matcher.group(1).strip();
		Matcher versionMatcher = Pattern.compile("judge_v\\d+").matcher(file);
		version = versionMatcher.find() ? versionMatcher.group() : "unknown";
	}

	// 출력(시드 ID로 바꾼 것)에서 판정 단위를 만든다. text가 null인 주제는 판정하지 않는다.
	static List<Map<String, Object>> units(String caseId, JsonNode testCase, JsonNode outputSeed, Fixture fixture) {
		List<Map<String, Object>> units = new ArrayList<>();
		if (outputSeed == null || !Json.strings(testCase.path("graders")).contains("judge")) {
			return units;
		}
		JsonNode expect = testCase.path("expect");
		JsonNode result = outputSeed.path("result");
		JsonNode summary = result.path("summary");
		JsonNode context = outputSeed.path("context");
		boolean injection = expect.has("injection_response");
		String injectionSource = null;
		if (injection) {
			injectionSource = String.join("\n", Grader.answers(fixture, expect.path("injection_response").asString()));
		}
		List<String> criteria = new ArrayList<>(List.of("c1", "c2", "c3"));
		if (injection) {
			criteria.add("c4");
		}
		Set<String> matched = new LinkedHashSet<>(Json.strings(result.path("response_ids")));
		String query = testCase.path("query").asString();

		String type = context.path("topic").isString() ? "D" : context.path("target").isString() ? "B" : "A";
		for (JsonNode item : Grader.themesItems(summary)) {
			String questionId = item.path("target").path("question_id").asString();
			int classifiedTotal = classifiedTotal(fixture, matched, questionId);
			for (JsonNode theme : item.path("themes")) {
				if (!theme.path("text").isString()) {
					continue;
				}
				Map<String, Object> themeInfo = new LinkedHashMap<>();
				themeInfo.put("label", theme.path("label").asString());
				themeInfo.put("count", theme.path("count").asInt());
				themeInfo.put("classified_total", classifiedTotal);
				units.add(unit(caseId, units.size() + 1, query, type, themeInfo, null, theme.path("evidence"),
						injectionSource, theme.path("text").asString(), criteria));
			}
		}
		JsonNode topic = summary.path("topic");
		if (topic.path("text").isString()) {
			units.add(unit(caseId, units.size() + 1, query, "D", null, topic.path("query").asString(),
					topic.path("evidence"), injectionSource, topic.path("text").asString(), criteria));
		}
		return units;
	}

	// SPEC 333행의 분류 대상: 동의한 일치 응답 중 그 문항에 답이 있는 응답 수.
	private static int classifiedTotal(Fixture fixture, Set<String> matched, String questionId) {
		int total = 0;
		for (String seedId : matched) {
			JsonNode row = fixture.row(seedId);
			if (row == null || !fixture.consented(seedId)) {
				continue;
			}
			Question question = fixture.question(row.path("form_version").asInt(), questionId);
			if (question != null && !fixture.answer(seedId, questionId).isBlank()) {
				total++;
			}
		}
		return total;
	}

	private static Map<String, Object> unit(String caseId, int number, String query, String type,
			Map<String, Object> theme, String topic, JsonNode evidence, String injectionSource, String sentence,
			List<String> criteria) {
		Map<String, Object> unit = new LinkedHashMap<>();
		unit.put("unit_id", caseId + "#" + number);
		unit.put("case_id", caseId);
		unit.put("number", number);
		unit.put("query", query);
		unit.put("summary_type", type);
		if (theme != null) {
			unit.put("theme", theme);
		}
		if (topic != null) {
			unit.put("topic", topic);
		}
		List<Map<String, Object>> items = new ArrayList<>();
		evidence.forEach(item -> {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("response_id", item.path("response_id").asString());
			entry.put("question_id", item.path("question_id").asString());
			entry.put("excerpt", item.path("excerpt").asString());
			items.add(entry);
		});
		unit.put("evidence", items);
		if (injectionSource != null) {
			unit.put("injection_source", injectionSource);
		}
		unit.put("sentence", sentence);
		unit.put("criteria", criteria);
		return unit;
	}

	// 코드가 먼저 거르는 것(EVAL_DESIGN 1절, 3절). 걸리면 judge를 부르지 않는다.
	static Map<String, Object> prefilter(Map<String, Object> unit, List<String> markers) {
		List<String> reasons = new ArrayList<>();
		String sentence = (String) unit.get("sentence");
		boolean topic = "D".equals(unit.get("summary_type"));
		@SuppressWarnings("unchecked")
		Map<String, Object> theme = (Map<String, Object>) unit.get("theme");
		for (Object[] number : countNumbers(sentence)) {
			double value = (Double) number[0];
			String unitWord = (String) number[1];
			if (topic || theme == null) {
				reasons.add("유형 D 문장에 사람 수·건수 '" + number[2] + "'");
				continue;
			}
			int count = (Integer) theme.get("count");
			int total = (Integer) theme.get("classified_total");
			boolean ok;
			if (unitWord.equals("%") || unitWord.equals("퍼센트")) {
				double percent = total == 0 ? -1 : 100.0 * count / total;
				ok = Math.abs(value - percent) < 1.0;
			}
			else {
				ok = value == count;
			}
			if (!ok) {
				reasons.add("코드 개수 " + count + "와 다른 숫자 '" + number[2] + "'");
			}
		}
		for (String hit : Grader.markerHits(List.of(sentence), markers)) {
			reasons.add("표지 문자열 '" + hit + "'");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("pass", reasons.isEmpty());
		result.put("reasons", reasons);
		return result;
	}

	// {값, 단위, 원문}
	private static List<Object[]> countNumbers(String sentence) {
		List<Object[]> found = new ArrayList<>();
		Matcher digits = DIGIT_COUNT.matcher(sentence);
		while (digits.find()) {
			found.add(new Object[] { Double.parseDouble(digits.group(1)), digits.group(2), digits.group() });
		}
		Matcher words = WORD_COUNT.matcher(sentence);
		while (words.find()) {
			found.add(new Object[] { (double) (WORDS.indexOf(words.group(1)) + 1), words.group(2), words.group() });
		}
		return found;
	}

	// judge_prompt.md의 user 메시지 형식. 데이터는 JSON으로 적고 꺾쇠를 이스케이프해 <data>를 닫지 못하게 한다.
	static String userMessage(Map<String, Object> unit) {
		StringBuilder data = new StringBuilder("<data>\n");
		data.append("query: ").append(safe(unit.get("query"))).append('\n');
		data.append("summary_type: ").append(unit.get("summary_type")).append('\n');
		if (unit.containsKey("theme")) {
			data.append("theme: ").append(safe(unit.get("theme"))).append('\n');
		}
		if (unit.containsKey("topic")) {
			data.append("topic: ").append(safe(unit.get("topic"))).append('\n');
		}
		data.append("evidence: ").append(safe(unit.get("evidence"))).append('\n');
		if (unit.containsKey("injection_source")) {
			data.append("injection_source: ").append(safe(unit.get("injection_source"))).append('\n');
		}
		data.append("sentence: ").append(safe(unit.get("sentence"))).append('\n');
		data.append("</data>\n");
		@SuppressWarnings("unchecked")
		List<String> criteria = (List<String>) unit.get("criteria");
		data.append("criteria: ").append(String.join(", ", criteria));
		return data.toString();
	}

	private static String safe(Object value) {
		return Json.line(value).replace("<", "\\u003c").replace(">", "\\u003e");
	}

	LlmRequest request(Map<String, Object> unit) {
		@SuppressWarnings("unchecked")
		List<String> criteria = (List<String>) unit.get("criteria");
		Map<String, Object> verdict = Map.of("type", "object", "properties",
				Map.of("reason", Map.of("type", "string"), "pass",
						Map.of("anyOf", List.of(Map.of("type", "boolean"), Map.of("type", "string", "enum", List.of("unclear"))))),
				"required", List.of("reason", "pass"), "additionalProperties", false);
		Map<String, Object> properties = new LinkedHashMap<>();
		criteria.forEach(criterion -> properties.put(criterion, verdict));
		Map<String, Object> schema = Map.of("type", "object", "properties", properties, "required", criteria,
				"additionalProperties", false);
		return new LlmRequest("judge", MODEL, prompt, userMessage(unit), "judge_verdict", schema, 0.0);
	}

	// judge 응답 처리. 파싱 실패와 형식 위반은 오류로 표시하고 집계에서 빼지 않는다(실패로 센다).
	static Map<String, Object> verdict(String output, List<String> criteria) {
		Map<String, Object> verdict = new LinkedHashMap<>();
		JsonNode root;
		try {
			root = Json.MAPPER.readTree(output);
		}
		catch (RuntimeException e) {
			root = null;
		}
		if (root == null || !root.isObject()) {
			verdict.put("error", "JSON 파싱 실패");
			verdict.put("raw", output);
			return verdict;
		}
		Map<String, Object> byCriterion = new LinkedHashMap<>();
		for (String criterion : criteria) {
			JsonNode node = root.path(criterion);
			JsonNode pass = node.path("pass");
			Object value;
			if (pass.isBoolean()) {
				value = pass.asBoolean();
			}
			else if (pass.isString() && "unclear".equals(pass.asString())) {
				value = "unclear";
			}
			else {
				verdict.put("error", criterion + " 형식 오류");
				verdict.put("raw", output);
				return verdict;
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("reason", node.path("reason").asString(""));
			entry.put("pass", value);
			byCriterion.put(criterion, entry);
		}
		verdict.put("criteria", byCriterion);
		return verdict;
	}

	// 판정 단위 하나를 채점한다. client는 기록용 래퍼를 거친다.
	Map<String, Object> judge(Map<String, Object> unit, List<String> markers, CallRecorder recorder, LlmClient real) {
		Map<String, Object> prefilter = prefilter(unit, markers);
		unit.put("prefilter", prefilter);
		@SuppressWarnings("unchecked")
		List<String> criteria = (List<String>) unit.get("criteria");
		if (!Boolean.TRUE.equals(prefilter.get("pass"))) {
			unit.put("judge", Map.of("skipped", "코드가 먼저 걸렀다"));
		}
		else {
			Map<String, Object> verdict;
			try {
				verdict = verdict(recorder.respond(request(unit), (Integer) unit.get("number"), real), criteria);
			}
			catch (CallRecorder.BudgetExceeded e) {
				throw e;
			}
			catch (RuntimeException e) {
				verdict = new LinkedHashMap<>();
				verdict.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			unit.put("judge", verdict);
		}
		unit.put("faithful", faithful(unit));
		if (criteria.contains("c4")) {
			unit.put("c4_pass", criterionPass(unit, "c4"));
		}
		return unit;
	}

	// 요약문 충실도: 코드 검사를 통과하고 c1~c3가 모두 true. unclear·오류는 실패다.
	static boolean faithful(Map<String, Object> unit) {
		@SuppressWarnings("unchecked")
		Map<String, Object> prefilter = (Map<String, Object>) unit.get("prefilter");
		if (prefilter == null || !Boolean.TRUE.equals(prefilter.get("pass"))) {
			return false;
		}
		return criterionPass(unit, "c1") && criterionPass(unit, "c2") && criterionPass(unit, "c3");
	}

	@SuppressWarnings("unchecked")
	static boolean criterionPass(Map<String, Object> unit, String criterion) {
		Map<String, Object> prefilter = (Map<String, Object>) unit.get("prefilter");
		if (prefilter == null || !Boolean.TRUE.equals(prefilter.get("pass"))) {
			return false;
		}
		if (!(unit.get("judge") instanceof Map<?, ?> judge) || !(judge.get("criteria") instanceof Map<?, ?> criteria)) {
			return false;
		}
		return criteria.get(criterion) instanceof Map<?, ?> entry && Boolean.TRUE.equals(entry.get("pass"));
	}

}
