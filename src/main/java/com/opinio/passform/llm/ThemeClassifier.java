package com.opinio.passform.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opinio.passform.domain.analysis.RagSettings;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 답마다 주제 라벨 하나를 붙인다. 개수는 세지 않는다(AC87). 응답 ID 대신 짧은 키를 보낸다.
@Component
public class ThemeClassifier {

	private static final String MODEL = "gpt-6-luna";

	private static final String PROMPT_PATH = "response_analysis/prompts/classify_themes.md";

	private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
			Map.of("labels",
					Map.of("type", "array", "items",
							Map.of("type", "object", "properties",
									Map.of("key", Map.of("type", "string"), "label", Map.of("type", "string")), "required",
									List.of("key", "label"), "additionalProperties", false))),
			"required", List.of("labels"), "additionalProperties", false);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final LlmClient client;

	private final int batchSize;

	private final String prompt;

	public ThemeClassifier(LlmClient client, RagSettings settings) {
		this.client = client;
		this.batchSize = settings.classifyBatchSize();
		this.prompt = PromptData.promptOf(PROMPT_PATH).replace("{max_themes}", String.valueOf(settings.maxThemes()));
	}

	// answers는 호출한 쪽의 ID → 가린 답이다. 보낸 ID마다 라벨 하나를 돌려준다.
	// 보내지 않은 키는 무시한다. 사람이 정한 것(2026-11-08): 라벨이 빠지거나 비면 재시도 없이 503이다.
	public Map<String, String> classify(String questionNote, Map<String, String> answers) {
		List<String> ids = new ArrayList<>(answers.keySet());
		Map<String, String> labels = new LinkedHashMap<>();
		Set<String> knownLabels = new LinkedHashSet<>();
		for (int start = 0; start < ids.size(); start += batchSize) {
			List<String> batch = ids.subList(start, Math.min(start + batchSize, ids.size()));
			List<Map<String, String>> items = new ArrayList<>();
			Map<String, String> idByKey = new LinkedHashMap<>();
			for (String id : batch) {
				String key = "a" + idByKey.size();
				idByKey.put(key, id);
				Map<String, String> item = new LinkedHashMap<>();
				item.put("key", key);
				item.put("text", answers.get(id));
				items.add(item);
			}
			Map<String, String> output = parse(client.respond(new LlmRequest("classify_themes", MODEL, prompt,
					input(questionNote, knownLabels, items), "theme_labels", SCHEMA, 0.0)));
			idByKey.forEach((key, id) -> {
				String label = output.get(key);
				if (label == null || label.isBlank()) {
					throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
				}
				labels.put(id, label.strip());
				knownLabels.add(label.strip());
			});
		}
		return labels;
	}

	private static String input(String questionNote, Set<String> knownLabels, List<Map<String, String>> items) {
		return PromptData.block("question", questionNote) + "\n" + PromptData.block("known_labels", knownLabels) + "\n"
				+ PromptData.block("answers", items);
	}

	private static Map<String, String> parse(String output) {
		JsonNode labels;
		try {
			labels = JSON.readTree(output).path("labels");
		}
		catch (JacksonException e) {
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
		Map<String, String> byKey = new LinkedHashMap<>();
		for (JsonNode item : labels) {
			byKey.putIfAbsent(item.path("key").asString(""), item.path("label").asString(""));
		}
		return byKey;
	}

}
