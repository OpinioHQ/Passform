package com.opinio.passform.service.analysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.opinio.passform.domain.analysis.Coverage;
import com.opinio.passform.domain.analysis.SummaryUnavailableVersion;
import com.opinio.passform.domain.analysis.VersionCoverage;

// analyze_response.schema.json의 200 응답. ok이면 clarify가 null이고, clarify이면 matched·coverage·result가 null이다.
public record AnalyzeView(String status, RequestContext context, List<MatchedView> matched, CoverageView coverage,
		ResultView result, ClarifyView clarify) {

	static AnalyzeView ok(RequestContext context, List<MatchedView> matched, CoverageView coverage, ResultView result) {
		return new AnalyzeView("ok", context, matched, coverage, result, null);
	}

	// 평가 함수의 Coverage(요약 범위 포함)에 list 정렬 범위를 더한 출력이다(SPEC 314행). 해당 범위가 없으면 키를 내지 않는다.
	@JsonInclude(Include.NON_NULL)
	public record CoverageView(int totalSubmittedCount, int notEvaluableCount, List<VersionCoverage> notEvaluableByVersion,
			List<Integer> sortUnavailableByVersion, List<SummaryUnavailableVersion> summaryUnavailableByVersion) {

		static CoverageView of(Coverage coverage, List<Integer> sortUnavailableByVersion) {
			return new CoverageView(coverage.totalSubmittedCount(), coverage.notEvaluableCount(),
					coverage.notEvaluableByVersion(), sortUnavailableByVersion, coverage.summaryUnavailableByVersion());
		}

	}

	static AnalyzeView clarify(RequestContext context, ClarifyView clarify) {
		return new AnalyzeView("clarify", context, null, null, null, clarify);
	}

	public record MatchedView(String condition, int formVersion, String questionId, String questionNote, String kind,
			String option) {
	}

	// summary는 summarize에서만 객체이고 count·list에서는 null이다.
	public record ResultView(String operation, int count, List<String> responseIds, SummaryView summary) {
	}

	// options는 사유에 따라 문자열이거나 QuestionOption이다(SPEC 336~347행).
	public record ClarifyView(String reason, String message, List<?> options) {
	}

	@JsonInclude(Include.NON_NULL)
	public record QuestionOption(int formVersion, String questionId, String questionNote, List<String> options) {
	}

}
