package com.opinio.passform.domain.analysis;

import java.util.List;

// SPEC 5절 313행: 제출 버전별 조건 참·거짓·판정 불가 판정과 all/any/not 결합 (match가 null이면 ALL)
public class ConditionEvaluator {

	public EvaluationResult evaluate(List<String> conditions, MatchMode match, List<ConditionMatch> matching,
			List<ResponseRecord> responses) {
		throw new UnsupportedOperationException("not implemented");
	}

}
