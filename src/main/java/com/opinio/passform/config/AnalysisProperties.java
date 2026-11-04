package com.opinio.passform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 모델 ID는 AGENTS 7절. 나머지는 SPEC 5절 332행의 임시값으로, 시드로 측정한 뒤 확정한다.
@ConfigurationProperties("passform.analysis")
public record AnalysisProperties(@DefaultValue("gpt-6-luna") String parserModel,
		@DefaultValue("gpt-6.1-terra") String summaryModel,
		@DefaultValue("text-embedding-3-small") String embeddingModel, @DefaultValue("50") int classifyBatchSize,
		@DefaultValue("300") int maxResponsesPerRequest, @DefaultValue("6") int maxThemes,
		@DefaultValue("3") int topK, @DefaultValue("0.2") double themeMinScore,
		@DefaultValue("0.35") double topicMinScore) {

	public static AnalysisProperties defaults() {
		return new AnalysisProperties("gpt-6-luna", "gpt-6.1-terra", "text-embedding-3-small", 50, 300, 6, 3, 0.2,
				0.35);
	}

}
