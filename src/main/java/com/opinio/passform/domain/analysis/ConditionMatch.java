package com.opinio.passform.domain.analysis;

// SPEC 5절 314행: matched[] — 조건·제출 버전별 매칭 근거 (option은 kind가 OPTION일 때만, 그 밖은 null)
public record ConditionMatch(String condition, int formVersion, String questionId, MatchKind kind, String option) {
}
