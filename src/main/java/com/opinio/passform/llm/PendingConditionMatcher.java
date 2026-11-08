package com.opinio.passform.llm;

import java.util.List;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.VersionQuestions;

// 운영 bean이 아니다(운영은 RuleFirstConditionMatcher + LlmConditionMatcher). LLM 없이 service를 시험할 때 쓰는 매처다.
// 조건이 있는데 "매칭 없음"을 돌려주면 unknown_condition으로 잘못 되묻게 되므로 503으로 멈춘다.
public class PendingConditionMatcher implements ConditionMatcher {

	@Override
	public List<ConditionMatch> match(List<String> conditions, List<VersionQuestions> versions) {
		if (conditions.isEmpty()) {
			return List.of();
		}
		throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
	}

}
