package com.opinio.passform.domain.analysis;

// config/rag.yaml의 값이다. 값은 그 파일에만 둔다.
public record RagSettings(int themeTopK, double themeMinScore, int topicTopK, double topicMinScore, int classifyBatchSize,
		int maxResponsesPerRequest, int maxThemes, int excerptMaxChars) {
}
