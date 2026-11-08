package com.opinio.passform.llm;

import java.util.Map;

// LLM 호출 한 번의 전부다. instructions에는 고정 프롬프트만, input에는 구분자로 감싼 데이터만 넣는다(AGENTS 5절 76행).
// temperature가 null이면 보내지 않는다.
public record LlmRequest(String purpose, String model, String instructions, String input, String schemaName,
		Map<String, Object> schema, Double temperature) {
}
