package com.opinio.passform.domain.analysis;

import java.util.List;

// SPEC 5절 314행: coverage — {total_submitted_count, not_evaluable_count, not_evaluable_by_version}
public record Coverage(int totalSubmittedCount, int notEvaluableCount, List<VersionCoverage> notEvaluableByVersion) {
}
