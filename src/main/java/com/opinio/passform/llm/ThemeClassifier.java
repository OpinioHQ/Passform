package com.opinio.passform.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opinio.passform.config.AnalysisProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 답마다 주제 라벨 하나만 붙인다. 개수와 순서는 코드가 정한다(AC87). 응답 ID는 보내지 않고 묶음 안의 키만 쓴다.
@Component
public class ThemeClassifier {

	private static final String PROMPT_PATH = "demo_prompts/classify_themes.md";

	private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
			Map.of("labels",
					Map.of("type", "array", "items",
							Map.of("type", "object", "properties",
									Map.of("key", Map.of("type", "string"), "label", Map.of("type", "string")),
									"required", List.of("key", "label"), "additionalProperties", false))),
			"required", List.of("labels"), "additionalProperties", false);

	private final LlmClient client;

	private final String model;

	private final int batchSize;

	private final int maxThemes;

	private final String prompt;

	private final JsonMapper mapper = JsonMapper.builder().build();

	public ThemeClassifier(LlmClient client, AnalysisProperties properties) {
		this.client = client;
		this.model = properties.parserModel();
		this.batchSize = properties.classifyBatchSize();
		this.maxThemes = properties.maxThemes();
		this.prompt = PromptLoader.load(PROMPT_PATH).replace("{max_themes}", String.valueOf(maxThemes));
	}

	// responseId → 가린 답. 돌려주는 값은 responseId → 라벨이다.
	public Map<String, String> classify(String questionNote, Map<String, String> answersByResponseId) {
		List<String> ids = List.copyOf(answersByResponseId.keySet());
		Map<String, String> labels = new LinkedHashMap<>();
		Set<String> knownLabels = new LinkedHashSet<>();
		for (int start = 0; start < ids.size(); start += batchSize) {
			List<String> batch = ids.subList(start, Math.min(start + batchSize, ids.size()));
			Map<String, String> keyed = new LinkedHashMap<>();
			Map<String, String> idByKey = new LinkedHashMap<>();
			for (int index = 0; index < batch.size(); index++) {
				keyed.put("a" + index, answersByResponseId.get(batch.get(index)));
				idByKey.put("a" + index, batch.get(index));
			}
			String input = "<question>\n" + mapper.writeValueAsString(questionNote) + "\n</question>\n<known_labels>\n"
					+ mapper.writeValueAsString(new ArrayList<>(knownLabels)) + "\n</known_labels>\n<answers>\n"
					+ mapper.writeValueAsString(keyed) + "\n</answers>";
			List<String> outputs = client.complete(new LlmClient.Request("classify_themes", model, prompt, input,
					"theme_labels", SCHEMA, true, true));
			readLabels(outputs, idByKey, labels, knownLabels);
		}
		return labels;
	}

	private void readLabels(List<String> outputs, Map<String, String> idByKey, Map<String, String> labels,
			Set<String> knownLabels) {
		for (String output : outputs) {
			try {
				JsonNode node = mapper.readTree(output);
				if (!node.has("labels")) {
					continue;
				}
				for (JsonNode item : node.path("labels")) {
					String responseId = idByKey.get(item.path("key").asString(""));
					String label = item.path("label").asString("");
					if (responseId != null) {
						labels.put(responseId, label);
						knownLabels.add(label);
					}
				}
				return;
			}
			catch (JacksonException exception) {
				// 다음 출력 항목을 본다. 라벨이 없는 답은 어떤 주제에도 세지 않는다.
			}
		}
	}

}
