package com.opinio.passform.llm;

import java.util.List;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.form.VersionQuestions;

// 조건 표현을 제출 버전별 문항·선택지에 연결한다(SPEC 5절 314행, 구현은 E7).
// 돌려주지 않은 (조건, 버전)은 그 버전에서 매칭 불가다. (조건, 버전)당 항목은 문항마다 하나다.
public interface ConditionMatcher {

	List<ConditionMatch> match(List<String> conditions, List<VersionQuestions> versions);

}
