package com.opinio.passform.service.analysis;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;

@Service
public class AnalysisService {

	public AnalysisService(FormRepository forms, FormVersionRepository formVersions, ResponseRepository responses,
			QueryParser parser, ConditionMatcher matcher, RequestContextSchema contextSchema) {
	}

	public AnalyzeView analyze(AnalyzeCommand command) {
		throw new IllegalStateException("구현 전 스텁");
	}

	public record AnalyzeCommand(UUID userId, String formId, String body) {
	}

}
