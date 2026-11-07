package com.opinio.passform.llm;

import java.util.List;

import org.springframework.stereotype.Component;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.VersionQuestions;

// E7의 실제 매처가 들어올 때까지의 운영 bean이다. E7이 이 클래스를 지운다.
// 조건이 있는데 "매칭 없음"을 돌려주면 unknown_condition으로 잘못 되묻게 되므로 503으로 멈춘다.
@Component
public class PendingConditionMatcher implements ConditionMatcher {

	@Override
	public List<ConditionMatch> match(List<String> conditions, List<VersionQuestions> versions) {
		if (conditions.isEmpty()) {
			return List.of();
		}
		throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
	}

}
