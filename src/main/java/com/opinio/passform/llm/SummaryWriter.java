package com.opinio.passform.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 근거만 보고 요약문을 쓴다. 보내는 것은 근거와 코드가 센 개수, 제출 버전과 그 버전의 문항 문구뿐이다(SPEC 332행).
@Component
public class SummaryWriter {

	private static final String MODEL = "gpt-6.1-terra";

	private static final String PROMPT_PATH = "response_analysis/prompts/summarize.md";

	private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
			Map.of("text", Map.of("type", "string")), "required", List.of("text"), "additionalProperties", false);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final LlmClient client;

	private final String prompt;

	public SummaryWriter(LlmClient client) {
		this.client = client;
		this.prompt = PromptData.promptOf(PROMPT_PATH);
	}

	public record EvidenceInput(int formVersion, String questionNote, String excerpt) {
	}

	public String writeTheme(String label, int count, List<EvidenceInput> evidence) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("theme", label);
		data.put("count", count);
		data.put("evidence", evidenceData(evidence));
		return write("summarize_theme", data);
	}

	// 유형 D에는 개수를 넣지 않는다(AC89).
	public String writeTopic(String topic, List<EvidenceInput> evidence) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("topic", topic);
		data.put("evidence", evidenceData(evidence));
		return write("summarize_topic", data);
	}

	private static List<Map<String, Object>> evidenceData(List<EvidenceInput> evidence) {
		return evidence.stream().map(item -> {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("form_version", item.formVersion());
			entry.put("question_note", item.questionNote());
			entry.put("excerpt", item.excerpt());
			return entry;
		}).toList();
	}

	// 사람이 정한 것(2026-11-08): 요약문이 비었거나 깨지면 재시도 없이 503이다.
	private String write(String purpose, Map<String, Object> data) {
		String output = client
			.respond(new LlmRequest(purpose, MODEL, prompt, PromptData.block("data", data), "summary_text", SCHEMA, null));
		String text;
		try {
			text = JSON.readTree(output).path("text").asString("");
		}
		catch (JacksonException e) {
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
		if (text.isBlank()) {
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
		return text.strip();
	}

}
