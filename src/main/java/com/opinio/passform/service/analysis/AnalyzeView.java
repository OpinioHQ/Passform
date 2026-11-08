package com.opinio.passform.service.analysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.opinio.passform.domain.analysis.Coverage;

// analyze_response.schema.json의 200 응답. ok이면 clarify가 null이고, clarify이면 matched·coverage·result가 null이다.
public record AnalyzeView(String status, RequestContext context, List<MatchedView> matched, Coverage coverage,
		ResultView result, ClarifyView clarify) {

	static AnalyzeView ok(RequestContext context, List<MatchedView> matched, Coverage coverage, ResultView result) {
		return new AnalyzeView("ok", context, matched, coverage, result, null);
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
