package com.opinio.passform.demo.llm;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.opinio.passform.demo.config.AnalysisProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 요약문은 근거와 코드가 센 개수만 보고 쓴다. 근거 원문은 구분자로 감싼 데이터 영역에만 넣는다(AC31, AGENTS 5절).
@Component
public class SummaryWriter {

	private static final String PROMPT_PATH = "demo_prompts/summarize.md";

	private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
			Map.of("text", Map.of("type", "string")), "required", List.of("text"), "additionalProperties", false);

	private final LlmClient client;

	private final String model;

	private final String prompt;

	private final JsonMapper mapper = JsonMapper.builder().build();

	public SummaryWriter(LlmClient client, AnalysisProperties properties) {
		this.client = client;
		this.model = properties.summaryModel();
		this.prompt = PromptLoader.load(PROMPT_PATH);
	}

	public record EvidenceInput(int formVersion, String questionNote, String excerpt) {
	}

	public String writeTheme(String label, int count, List<EvidenceInput> evidence) {
		return write(Map.of("theme", label, "count", count, "evidence", evidence));
	}

	public String writeTopic(String topic, List<EvidenceInput> evidence) {
		return write(Map.of("topic", topic, "evidence", evidence));
	}

	private String write(Map<String, Object> data) {
		String input = "<data>\n" + mapper.writeValueAsString(data) + "\n</data>";
		List<String> outputs = client.complete(
				new LlmClient.Request("summarize", model, prompt, input, "summary_text", SCHEMA, true, false));
		for (String output : outputs) {
			try {
				JsonNode node = mapper.readTree(output);
				if (node.hasNonNull("text") && !node.get("text").asString().isBlank()) {
					return node.get("text").asString();
				}
			}
			catch (JacksonException exception) {
				// 다음 출력 항목을 본다.
			}
		}
		return null;
	}

}
