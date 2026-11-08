package com.opinio.passform.domain.analysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

// SPEC 5절 314행: coverage — {total_submitted_count, not_evaluable_count, not_evaluable_by_version, summary_unavailable_by_version?}
@JsonInclude(Include.NON_NULL)
public record Coverage(int totalSubmittedCount, int notEvaluableCount, List<VersionCoverage> notEvaluableByVersion,
		List<SummaryUnavailableVersion> summaryUnavailableByVersion) {

	public Coverage(int totalSubmittedCount, int notEvaluableCount, List<VersionCoverage> notEvaluableByVersion) {
		this(totalSubmittedCount, notEvaluableCount, notEvaluableByVersion, null);
	}

	// 해당 버전이 없으면 키를 내지 않는다.
	public Coverage withSummaryUnavailable(List<SummaryUnavailableVersion> versions) {
		return new Coverage(totalSubmittedCount, notEvaluableCount, notEvaluableByVersion,
				versions.isEmpty() ? null : List.copyOf(versions));
	}

}
