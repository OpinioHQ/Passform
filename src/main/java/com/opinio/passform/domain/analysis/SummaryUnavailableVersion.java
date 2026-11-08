package com.opinio.passform.domain.analysis;

// SPEC 314행: summarize의 target 문항이 없는 버전과 그 버전의 일치 응답 수.
public record SummaryUnavailableVersion(int formVersion, int responseCount) {
}
