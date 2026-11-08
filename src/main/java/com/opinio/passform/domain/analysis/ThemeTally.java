package com.opinio.passform.domain.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

// 주제별 개수와 순서는 코드가 라벨을 세어 정한다(AGENTS 3절 3항, AC87). LLM 출력의 개수를 쓰지 않는다.
public final class ThemeTally {

	public static final String OTHER = "기타";

	private static final Comparator<Theme> ORDER = Comparator.comparingInt(Theme::count)
		.reversed()
		.thenComparing(theme -> theme.responseIds().get(0));

	private ThemeTally() {
	}

	public record Theme(String label, int count, List<String> responseIds) {
	}

	// labelByResponseId는 그 요청에서 보낸 답의 라벨만 담는다.
	// 사람이 정한 것(2026-11-08): 라벨 종류가 상한을 넘으면 일반 라벨은 순서대로 상한-1개만 남기고 나머지를 기타로 합친다.
	public static List<Theme> tally(Map<String, String> labelByResponseId, int maxThemes) {
		Map<String, TreeSet<String>> idsByLabel = new TreeMap<>();
		labelByResponseId.forEach((id, label) -> idsByLabel.computeIfAbsent(label, key -> new TreeSet<>()).add(id));

		TreeSet<String> otherIds = idsByLabel.containsKey(OTHER) ? idsByLabel.remove(OTHER) : new TreeSet<>();
		List<Theme> general = new ArrayList<>(
				idsByLabel.entrySet().stream().map(entry -> theme(entry.getKey(), entry.getValue())).sorted(ORDER).toList());

		int distinct = general.size() + (otherIds.isEmpty() ? 0 : 1);
		if (distinct > maxThemes) {
			int kept = Math.max(maxThemes - 1, 0);
			general.subList(kept, general.size()).forEach(theme -> otherIds.addAll(theme.responseIds()));
			general = new ArrayList<>(general.subList(0, kept));
		}
		if (!otherIds.isEmpty()) {
			general.add(theme(OTHER, otherIds));
		}
		return List.copyOf(general);
	}

	private static Theme theme(String label, TreeSet<String> ids) {
		return new Theme(label, ids.size(), List.copyOf(ids));
	}

}
