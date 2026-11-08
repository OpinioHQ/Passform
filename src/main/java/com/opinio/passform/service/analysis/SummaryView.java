package com.opinio.passform.service.analysis;

import java.util.List;

// analyze_response.schema.json의 result.summary다(SPEC 325행).
public record SummaryView(String status, int excludedNoConsentCount, List<SummaryItem> items, TopicView topic) {

	public sealed interface SummaryItem permits ThemesItem, OptionStatsItem {

		String type();

	}

	public record ThemesItem(String type, TargetView target, List<ThemeView> themes) implements SummaryItem {

		static ThemesItem of(TargetView target, List<ThemeView> themes) {
			return new ThemesItem("themes", target, themes);
		}

	}

	public record OptionStatsItem(String type, TargetView target, List<OptionView> options) implements SummaryItem {

		static OptionStatsItem of(TargetView target, List<OptionView> options) {
			return new OptionStatsItem("option_stats", target, options);
		}

	}

	public record TargetView(String questionId, String questionNote, List<Integer> formVersions) {
	}

	public record ThemeView(String label, int count, List<String> responseIds, String text, List<EvidenceView> evidence) {
	}

	public record OptionView(String label, int count, List<Integer> formVersions) {
	}

	// 유형 D. 개수 필드를 두지 않는다(AC89).
	public record TopicView(String query, String text, List<EvidenceView> evidence) {
	}

	public record EvidenceView(String responseId, int formVersion, String questionId, String excerpt) {
	}

}
