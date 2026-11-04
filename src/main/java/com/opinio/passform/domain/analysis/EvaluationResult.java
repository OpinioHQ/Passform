package com.opinio.passform.domain.analysis;

import java.util.List;

// SPEC 5절 246행: analyze 출력의 result.count·response_ids, matched, coverage
public record EvaluationResult(int count, List<String> responseIds, List<ConditionMatch> matched, Coverage coverage) {
}
