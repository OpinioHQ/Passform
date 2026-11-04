package com.opinio.passform.demo.service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opinio.passform.demo.domain.analysis.RequestContext;

// POST /forms/{form_id}/analyze 출력(SPEC 5절 246~303행).
public record AnalysisResponse(String status, RequestContext context, List<MatchedEvidence> matched,
		CoverageView coverage, ResultView result, ClarifyView clarify) {

	static AnalysisResponse ok(RequestContext context, List<MatchedEvidence> matched, CoverageView coverage,
			ResultView result) {
		return new AnalysisResponse("ok", context, matched, coverage, result, null);
	}

	static AnalysisResponse clarify(RequestContext context, String reason, String message, List<?> options) {
		return new AnalysisResponse("clarify", context, null, null, null, new ClarifyView(reason, message, options));
	}

	// question_note는 SPEC 253행에 없다. 제작자 화면에 문항 번호 대신 문구를 보여 주려고 더했다(SPEC 수정 후보).
	public record MatchedEvidence(String condition, int formVersion, String questionId, String questionNote,
			String kind, String option) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CoverageView(int totalSubmittedCount, int notEvaluableCount,
			List<VersionCoverageView> notEvaluableByVersion, List<Integer> sortUnavailableByVersion,
			List<VersionCount> summaryUnavailableByVersion) {
	}

	public record VersionCoverageView(int formVersion, int responseCount, List<String> unavailableConditions) {
	}

	public record VersionCount(int formVersion, int responseCount) {
	}

	public record ResultView(String operation, int count, List<String> responseIds, SummaryView summary) {
	}

	public record ClarifyView(String reason, String message, List<?> options) {
	}

	public record SummaryView(String status, int excludedNoConsentCount, List<SummaryItem> items, TopicView topic) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SummaryItem(String type, TargetView target, List<ThemeView> themes, List<OptionView> options) {
	}

	public record TargetView(String questionId, String questionNote, List<Integer> formVersions) {
	}

	public record ThemeView(String label, int count, List<String> responseIds, String text,
			List<EvidenceView> evidence) {
	}

	public record OptionView(String label, int count, List<Integer> formVersions) {
	}

	public record EvidenceView(String responseId, int formVersion, String questionId, String excerpt) {
	}

	public record TopicView(String query, String text, List<EvidenceView> evidence) {
	}

	public record ChoiceQuestionOption(int formVersion, String questionId, String questionNote, List<String> options) {
	}

	public record QuestionOption(int formVersion, String questionId, String questionNote) {
	}

}
