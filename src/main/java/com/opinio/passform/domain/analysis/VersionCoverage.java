package com.opinio.passform.domain.analysis;

import java.util.List;

// SPEC 5절 314행: coverage.not_evaluable_by_version[] — {form_version, response_count, unavailable_conditions}
public record VersionCoverage(int formVersion, int responseCount, List<String> unavailableConditions) {
}
