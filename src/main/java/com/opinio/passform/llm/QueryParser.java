package com.opinio.passform.llm;

// 자연어 → RequestContext 원문(AC30). 검증과 재호출 판단은 호출하는 쪽이 코드로 한다. 입력은 제작자의 질의뿐이다.
public interface QueryParser {

	// previousError는 형식 오류로 다시 부를 때의 오류 설명이고, 첫 호출에서는 null이다.
	// 호출 실패와 키 없음은 DomainException(LLM_UNAVAILABLE)이다.
	String parse(String query, String previousError);

}
