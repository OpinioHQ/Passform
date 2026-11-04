package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// 폼 정의와 답은 내보내기 JSON과 같은 모양의 문자열로 저장한다(SPEC 5절 241행).
public final class FormJson {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private FormJson() {
	}

	public static FormDefinition parseDefinition(String json) {
		return MAPPER.readValue(json, FormDefinition.class);
	}

	public static JsonNode parseTree(String json) {
		return MAPPER.readTree(json);
	}

	public static Map<String, List<String>> parseAnswers(String json) {
		return answersFrom(MAPPER.readTree(json));
	}

	// [{question_id, value}] → 문항별 값 목록. 단일 값은 한 칸짜리 목록이 된다.
	public static Map<String, List<String>> answersFrom(JsonNode array) {
		Map<String, List<String>> answers = new LinkedHashMap<>();
		for (JsonNode answer : array) {
			JsonNode value = answer.path("value");
			List<String> values = new ArrayList<>();
			if (value.isArray()) {
				value.forEach(item -> values.add(item.asString()));
			}
			else if (!value.isNull() && !value.isMissingNode()) {
				values.add(value.asString());
			}
			answers.put(answer.path("question_id").asString(), values);
		}
		return answers;
	}

	public static String answersToJson(FormDefinition definition, Map<String, List<String>> answers) {
		ArrayNode array = MAPPER.createArrayNode();
		answers.forEach((questionId, values) -> {
			ObjectNode answer = array.addObject();
			answer.put("question_id", questionId);
			boolean checkbox = definition.question(questionId).map(Question::isCheckbox).orElse(values.size() > 1);
			if (checkbox) {
				ArrayNode valueArray = answer.putArray("value");
				values.forEach(valueArray::add);
			}
			else {
				answer.put("value", values.isEmpty() ? null : values.get(0));
			}
		});
		return MAPPER.writeValueAsString(array);
	}

	// checkbox는 배열, 나머지는 문자열로 돌려준다(data/seed README 가정 1).
	public static Object displayValue(Question question, List<String> values) {
		if (question != null && question.isCheckbox()) {
			return values;
		}
		return values.isEmpty() ? null : values.get(0);
	}

}
