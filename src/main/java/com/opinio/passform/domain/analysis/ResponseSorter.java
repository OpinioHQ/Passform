package com.opinio.passform.domain.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.opinio.passform.domain.form.Question;

// AC28, AC61: 값 순으로 정렬하고 빈 값·문항 없는 버전은 맨 뒤, 같은 값은 response_id 오름차순이다.
public final class ResponseSorter {

	private ResponseSorter() {
	}

	public record Sorted(List<String> responseIds, List<Integer> sortUnavailableVersions) {
	}

	// 정렬할 수 없으면 비어 있다. 호출하는 쪽이 unsortable_field로 되묻는다.
	public static Optional<Sorted> sort(List<ResponseRecord> matched, Map<Integer, Question> questionByVersion,
			String order) {
		Optional<Map<String, BigDecimal>> keys = sortKeys(matched, questionByVersion);
		if (keys.isEmpty()) {
			return Optional.empty();
		}
		Comparator<BigDecimal> byValue = "desc".equals(order) ? Comparator.reverseOrder() : Comparator.naturalOrder();
		Comparator<ResponseRecord> comparator = Comparator
			.comparing((ResponseRecord response) -> keys.get().get(response.responseId()),
					Comparator.nullsLast(byValue))
			.thenComparing(ResponseRecord::responseId);
		List<String> ids = matched.stream().sorted(comparator).map(ResponseRecord::responseId).toList();
		Set<Integer> unavailable = matched.stream()
			.map(ResponseRecord::formVersion)
			.filter(version -> !questionByVersion.containsKey(version))
			.collect(Collectors.toCollection(TreeSet::new));
		return Optional.of(new Sorted(ids, List.copyOf(unavailable)));
	}

	public static boolean isSortableType(Question question) {
		return question.isSingleChoice() || "short_answer".equals(question.questionType());
	}

	// 되묻기 후보로 보여 줄 문항. 단답은 값이 숫자·날짜일 때만 정렬할 수 있다(AC28).
	public static boolean isSortable(Question question, List<ResponseRecord> responses) {
		if (question.isSingleChoice()) {
			return true;
		}
		if (!"short_answer".equals(question.questionType()) || question.isPersonal()) {
			return false;
		}
		List<String> values = responses.stream()
			.flatMap(response -> response.answers().getOrDefault(question.questionId(), List.of()).stream())
			.map(String::trim)
			.toList();
		return !values.isEmpty() && (values.stream().allMatch(ResponseSorter::isNumber)
				|| values.stream().allMatch(ResponseSorter::isDate));
	}

	private static Optional<Map<String, BigDecimal>> sortKeys(List<ResponseRecord> matched,
			Map<Integer, Question> questionByVersion) {
		Collection<Question> questions = questionByVersion.values();
		if (questions.isEmpty() || !questions.stream().allMatch(ResponseSorter::isSortableType)) {
			return Optional.empty();
		}
		boolean allChoice = questions.stream().allMatch(Question::isSingleChoice);
		if (!allChoice && questions.stream().anyMatch(Question::isSingleChoice)) {
			return Optional.empty();
		}
		Map<String, String> values = new HashMap<>();
		for (ResponseRecord response : matched) {
			Question question = questionByVersion.get(response.formVersion());
			List<String> answer = (question != null)
					? response.answers().getOrDefault(question.questionId(), List.of()) : List.of();
			if (!answer.isEmpty()) {
				values.put(response.responseId(), answer.get(0).trim());
			}
		}
		if (allChoice) {
			return optionRanking(questions).map(ranking -> toKeys(values,
					value -> BigDecimal.valueOf(ranking.indexOf(value))));
		}
		if (values.values().stream().allMatch(ResponseSorter::isNumber)) {
			return Optional.of(toKeys(values, BigDecimal::new));
		}
		if (values.values().stream().allMatch(ResponseSorter::isDate)) {
			return Optional.of(toKeys(values, value -> BigDecimal.valueOf(LocalDate.parse(value).toEpochDay())));
		}
		return Optional.empty();
	}

	private static Map<String, BigDecimal> toKeys(Map<String, String> values,
			java.util.function.Function<String, BigDecimal> converter) {
		Map<String, BigDecimal> keys = new HashMap<>();
		values.forEach((responseId, value) -> keys.put(responseId, converter.apply(value)));
		return keys;
	}

	// 버전 사이에서 공통 선택지의 앞뒤 순서가 같아야 비교할 수 있다. 가장 긴 선택지 목록을 기준으로 삼는다.
	private static Optional<List<String>> optionRanking(Collection<Question> questions) {
		List<String> longest = questions.stream()
			.map(Question::options)
			.max(Comparator.comparingInt(List::size))
			.orElse(List.of());
		for (Question question : questions) {
			int previous = -1;
			for (String option : question.options()) {
				int rank = longest.indexOf(option);
				if (rank <= previous) {
					return Optional.empty();
				}
				previous = rank;
			}
		}
		return Optional.of(longest);
	}

	private static boolean isNumber(String value) {
		try {
			new BigDecimal(value);
			return true;
		}
		catch (NumberFormatException exception) {
			return false;
		}
	}

	private static boolean isDate(String value) {
		try {
			LocalDate.parse(value);
			return true;
		}
		catch (DateTimeParseException exception) {
			return false;
		}
	}

}
