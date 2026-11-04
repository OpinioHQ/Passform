package com.opinio.passform.domain.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// AC87: 주제별 개수와 순서는 코드가 라벨을 집계해 정한다. 보낸 응답이 아닌 ID의 라벨은 세지 않는다.
public final class ThemeAggregator {

	public static final String OTHER = "기타";

	private ThemeAggregator() {
	}

	public record Theme(String label, List<String> responseIds) {

		public int count() {
			return responseIds.size();
		}

	}

	public static List<Theme> aggregate(Collection<String> sentResponseIds, Map<String, String> labelByResponseId,
			int maxThemes) {
		Map<String, TreeSet<String>> grouped = new LinkedHashMap<>();
		for (String responseId : sentResponseIds) {
			String label = labelByResponseId.get(responseId);
			if (label == null) {
				continue;
			}
			String cleaned = label.isBlank() ? OTHER : label.trim();
			grouped.computeIfAbsent(cleaned, key -> new TreeSet<>()).add(responseId);
		}
		List<Theme> ordered = new ArrayList<>(grouped.entrySet().stream()
			.filter(entry -> !OTHER.equals(entry.getKey()))
			.map(entry -> new Theme(entry.getKey(), List.copyOf(entry.getValue())))
			.sorted(Comparator.comparingInt(Theme::count).reversed()
				.thenComparing(theme -> theme.responseIds().get(0)))
			.toList());
		TreeSet<String> others = new TreeSet<>(grouped.getOrDefault(OTHER, new TreeSet<>()));
		// 주제 수 상한을 넘은 작은 주제는 기타로 합친다. 기타는 항상 맨 뒤다.
		int limit = Math.max(1, others.isEmpty() && ordered.size() <= maxThemes ? maxThemes : maxThemes - 1);
		while (ordered.size() > limit) {
			others.addAll(ordered.remove(ordered.size() - 1).responseIds());
		}
		if (!others.isEmpty()) {
			ordered.add(new Theme(OTHER, List.copyOf(others)));
		}
		return ordered;
	}

}
