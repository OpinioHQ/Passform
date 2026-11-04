package com.opinio.passform.demo.llm;

import java.util.List;
import java.util.Map;

// 외부 호출은 인터페이스 뒤에 두고 테스트에서 모의 객체로 바꾼다(AGENTS 5절).
public interface LlmClient {

	// 모델이 낸 출력 문자열 전부. 여분의 message 항목이 붙을 수 있어 호출하는 쪽이 스키마에 맞는 것을 고른다.
	List<String> complete(Request request);

	List<float[]> embed(String purpose, List<String> inputs);

	record Request(String purpose, String model, String instructions, String input, String schemaName,
			Map<String, Object> schema, boolean strict, boolean zeroTemperature) {
	}

}
