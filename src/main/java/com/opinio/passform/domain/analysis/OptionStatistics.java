package com.opinio.passform.domain.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.Question;

// AC19 통합 보기: question_id·문구·유형이 같은 문항끼리 묶고 같은 선택지 문구만 합친다. 코드로만 계산한다.
public final class OptionStatistics {

	private OptionStatistics() {
	}

	public record Item(String questionId, String questionNote, String questionType, List<Integer> formVersions,
			List<OptionCount> options) {
	}

	public record OptionCount(String label, int count, List<Integer> formVersions) {
	}

	public static List<Item> combine(Map<Integer, FormDefinition> definitions, List<ResponseRecord> responses) {
		Map<Integer, List<ResponseRecord>> byVersion = responses.stream()
			.collect(Collectors.groupingBy(ResponseRecord::formVersion, TreeMap::new, Collectors.toList()));
		Map<String, Builder> builders = new LinkedHashMap<>();
		byVersion.forEach((version, versionResponses) -> {
			for (Question question : definitions.get(version).questions()) {
				if (!question.isChoice()) {
					continue;
				}
				String key = question.questionId() + "\u0000" + question.questionNote() + "\u0000"
						+ question.questionType();
				builders.computeIfAbsent(key, ignored -> new Builder(question)).add(version, question, versionResponses);
			}
		});
		return builders.values().stream().map(Builder::build).toList();
	}

	private static final class Builder {

		private final Question first;

		private final TreeSet<Integer> versions = new TreeSet<>();

		private final Map<String, Integer> counts = new LinkedHashMap<>();

		private final Map<String, TreeSet<Integer>> optionVersions = new LinkedHashMap<>();

		Builder(Question first) {
			this.first = first;
		}

		void add(int version, Question question, List<ResponseRecord> responses) {
			versions.add(version);
			for (String option : question.options()) {
				counts.putIfAbsent(option, 0);
				optionVersions.computeIfAbsent(option, ignored -> new TreeSet<>()).add(version);
			}
			for (ResponseRecord response : responses) {
				for (String value : response.answers().getOrDefault(question.questionId(), List.of())) {
					if (counts.containsKey(value)) {
						counts.merge(value, 1, Integer::sum);
					}
				}
			}
		}

		Item build() {
			List<OptionCount> options = new ArrayList<>();
			counts.forEach((label, count) -> options
				.add(new OptionCount(label, count, List.copyOf(optionVersions.get(label)))));
			return new Item(first.questionId(), first.questionNote(), first.questionType(), List.copyOf(versions),
					options);
		}

	}

}
