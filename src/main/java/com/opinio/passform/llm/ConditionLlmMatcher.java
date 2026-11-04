package com.opinio.passform.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.opinio.passform.config.AnalysisProperties;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.Question;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 스파이크 1차 프롬프트(미달 37/40)를 그대로 쓴다. 규칙으로 못 푼 조건에만 부르고, 허용 값 밖의 출력은 버린다.
// 응답 원문은 보내지 않는다. 문항·선택지와 조건 표현만 보낸다.
@Component
public class ConditionLlmMatcher {

	private static final String PROMPT_PATH = "demo_prompts/match_conditions.md";

	private static final String NUMBERING_LATEST = "'N번 문항'은 최신 버전에서 N번인 문항(latest_number가 N인 문항)이다. "
			+ "다른 버전에서는 같은 question_id의 문항으로 판단하고, 그 버전에 그 question_id가 없으면 NONE이다.";

	private static final String NONE = "NONE";

	private final LlmClient client;

	private final String model;

	private final String prompt;

	private final JsonMapper mapper = JsonMapper.builder().build();

	public ConditionLlmMatcher(LlmClient client, AnalysisProperties properties) {
		this.client = client;
		this.model = properties.parserModel();
		this.prompt = PromptLoader.load(PROMPT_PATH).replace("{numbering_rule}", NUMBERING_LATEST);
	}

	// 조건별·버전별 매칭. 매칭 불가는 항목을 만들지 않는다.
	public List<ConditionMatch> match(List<String> conditions, Map<Integer, FormDefinition> versions, int latestVersion) {
		Map<String, Object> schema = schema(conditions, versions);
		List<String> outputs = client.complete(new LlmClient.Request("match_conditions", model, prompt,
				userMessage(conditions, versions, latestVersion), "condition_match", schema, true, true));
		for (String output : outputs) {
			Optional<List<ConditionMatch>> parsed = parse(output, conditions, versions);
			if (parsed.isPresent()) {
				return parsed.get();
			}
		}
		return List.of();
	}

	private Optional<List<ConditionMatch>> parse(String output, List<String> conditions,
			Map<Integer, FormDefinition> versions) {
		JsonNode node;
		try {
			node = mapper.readTree(output);
		}
		catch (JacksonException exception) {
			return Optional.empty();
		}
		List<ConditionMatch> matches = new ArrayList<>();
		for (int index = 0; index < conditions.size(); index++) {
			JsonNode byVersion = node.get("c" + index);
			if (byVersion == null) {
				return Optional.empty();
			}
			for (Map.Entry<Integer, FormDefinition> version : versions.entrySet()) {
				String value = byVersion.path("v" + version.getKey()).asString(NONE);
				if (!allowedValues(version.getValue()).contains(value)) {
					return Optional.empty();
				}
				if (!NONE.equals(value)) {
					matches.add(toMatch(conditions.get(index), version.getKey(), value));
				}
			}
		}
		return Optional.of(matches);
	}

	private static ConditionMatch toMatch(String condition, int version, String value) {
		String[] parts = value.split("\\|", 3);
		return switch (parts[1]) {
			case "option" -> new ConditionMatch(condition, version, parts[0], MatchKind.OPTION, parts[2]);
			case "answered" -> new ConditionMatch(condition, version, parts[0], MatchKind.ANSWERED, null);
			default -> new ConditionMatch(condition, version, parts[0], MatchKind.UNANSWERED, null);
		};
	}

	private static List<String> allowedValues(FormDefinition definition) {
		List<String> values = new ArrayList<>();
		for (Question question : definition.questions()) {
			question.options().forEach(option -> values.add(question.questionId() + "|option|" + option));
			values.add(question.questionId() + "|answered");
			values.add(question.questionId() + "|unanswered");
		}
		values.add(NONE);
		return values;
	}

	private static Map<String, Object> schema(List<String> conditions, Map<Integer, FormDefinition> versions) {
		Map<String, Object> versionProperties = new LinkedHashMap<>();
		versions.forEach((version, definition) -> versionProperties.put("v" + version,
				Map.of("type", "string", "enum", allowedValues(definition))));
		Map<String, Object> condition = Map.of("type", "object", "properties", versionProperties, "required",
				List.copyOf(versionProperties.keySet()), "additionalProperties", false);
		Map<String, Object> properties = new LinkedHashMap<>();
		for (int index = 0; index < conditions.size(); index++) {
			properties.put("c" + index, condition);
		}
		return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()),
				"additionalProperties", false);
	}

	private String userMessage(List<String> conditions, Map<Integer, FormDefinition> versions, int latestVersion) {
		Map<String, String> keyed = new LinkedHashMap<>();
		for (int index = 0; index < conditions.size(); index++) {
			keyed.put("c" + index, conditions.get(index));
		}
		Map<String, Integer> latestNumbers = new HashMap<>();
		FormDefinition latest = versions.get(latestVersion);
		if (latest != null) {
			List<Question> questions = latest.questions();
			for (int index = 0; index < questions.size(); index++) {
				latestNumbers.put(questions.get(index).questionId(), index + 1);
			}
		}
		List<Map<String, Object>> versionPayload = new ArrayList<>();
		versions.forEach((version, definition) -> {
			List<Map<String, Object>> questions = new ArrayList<>();
			List<Question> ordered = definition.questions();
			for (int index = 0; index < ordered.size(); index++) {
				Question question = ordered.get(index);
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("number", index + 1);
				item.put("latest_number", latestNumbers.get(question.questionId()));
				item.put("question_id", question.questionId());
				item.put("question_note", question.questionNote());
				item.put("question_type", question.questionType());
				item.put("options", question.options());
				questions.add(item);
			}
			versionPayload.add(Map.of("form_version", version, "questions", questions));
		});
		return "<conditions>\n" + mapper.writeValueAsString(keyed) + "\n</conditions>\n<form>\n"
				+ mapper.writeValueAsString(Map.of("versions", versionPayload)) + "\n</form>";
	}

}
