package com.opinio.passform.llm;

import java.util.List;

// 임베딩의 외부 호출 경계다. 입력과 같은 순서로 벡터를 돌려준다. 호출에 실패하면 DomainException(LLM_UNAVAILABLE)이다.
public interface EmbeddingClient {

	List<double[]> embed(String purpose, List<String> texts);

}
