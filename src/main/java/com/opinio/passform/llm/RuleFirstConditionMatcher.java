package com.opinio.passform.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.LiteralMatcher;
import com.opinio.passform.domain.form.VersionQuestions;

// 운영 매처. 글자가 같은 (조건, 버전)은 규칙으로 풀고, 못 푼 (조건, 버전)만 다음 매처에 보낸다(analyze_api.md 결정 A9).
// 다음 매처가 돌려준 항목은 보낸 (조건, 버전)이고 그 버전의 실제 문항·선택지인 것만 받는다.
@Component
public class RuleFirstConditionMatcher implements ConditionMatcher {

	private final ConditionMatcher next;

	// 운영: 못 푼 조건은 LLM 매처로 간다. 키가 없거나 호출이 실패하면 LlmClient가 503(LLM_UNAVAILABLE)을 낸다.
	@Autowired
	public RuleFirstConditionMatcher(LlmConditionMatcher llm) {
		this(llm::match);
	}

	RuleFirstConditionMatcher(ConditionMatcher next) {
		this.next = next;
	}

	@Override
	public List<ConditionMatch> match(List<String> conditions, List<VersionQuestions> versions) {
		List<ConditionMatch> matches = new ArrayList<>();
		// 조건마다 못 푼 버전 목록. 같은 버전 목록끼리 묶어 한 번에 보낸다.
		Map<List<VersionQuestions>, Set<String>> unresolved = new LinkedHashMap<>();
		for (String condition : new LinkedHashSet<>(conditions)) {
			List<VersionQuestions> pending = new ArrayList<>();
			for (VersionQuestions version : versions) {
				List<ConditionMatch> literal = LiteralMatcher.match(condition, version);
				if (literal.isEmpty()) {
					pending.add(version);
				}
				matches.addAll(literal);
			}
			if (!pending.isEmpty()) {
				unresolved.computeIfAbsent(List.copyOf(pending), key -> new LinkedHashSet<>()).add(condition);
			}
		}
		unresolved.forEach((pending, pendingConditions) -> {
			for (ConditionMatch entry : next.match(List.copyOf(pendingConditions), pending)) {
				boolean sent = pendingConditions.contains(entry.condition());
				if (sent && pending.stream().anyMatch(version -> LiteralMatcher.allowed(entry, version))) {
					matches.add(entry);
				}
			}
		});
		return List.copyOf(matches);
	}

}
