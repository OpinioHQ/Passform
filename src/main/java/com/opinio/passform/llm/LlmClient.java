package com.opinio.passform.llm;

// 요약 LLM의 외부 호출 경계다. 테스트는 이 경계를 모의 객체로 바꿔 실제 요청 본문을 본다.
public interface LlmClient {

	// 구조화 출력 문자열을 돌려준다. 호출에 실패하면 DomainException(LLM_UNAVAILABLE)이다.
	String respond(LlmRequest request);

}
