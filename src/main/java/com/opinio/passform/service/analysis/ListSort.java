package com.opinio.passform.service.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.opinio.passform.domain.analysis.LiteralMatcher;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;

// list의 sort.by 해석과 정렬(SPEC 5절 311행, AC28, AC61, AGENTS 5절). LLM 없이 코드로 한다.
// sort.by는 규칙 매처와 같은 방식(문항 문구 글자 일치)으로 각 제출 버전에서 찾는다.
final class ListSort {

	private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private final List<Located> located;

	private final Map<Integer, FormDefinition> forms;

	private final List<ResponseRecord> records;

	ListSort(String by, List<VersionQuestions> versions, Map<Integer, FormDefinition> forms, List<ResponseRecord> records) {
		this.located = locate(by, versions);
		this.forms = forms;
		this.records = records;
	}

	record Located(int formVersion, Question question) {
	}

	// 버전 오름차순, 버전 안에서 문항 순서다. 비어 있으면 어느 버전에도 없다(표 6행).
	List<Located> located() {
		return located;
	}

	// 표 9행: 버전 사이에서 유형·값 형식·선택지(글자와 순서)가 같고, 숫자·날짜 단답은 모든 제출 값이 그 형식이어야 정렬한다.
	// 선택지는 완전히 같을 때만 비교한다. 추가만 된 경우도 되묻는다(사람 결정 2026-11-08).
	boolean sortable() {
		return sortable(located);
	}

	// 되묻기 후보: 그 문구로 정렬하면 되는 문항(버전 번호를 붙여 버전 오름차순).
	List<Located> sortableQuestions(List<VersionQuestions> versions) {
		List<Located> candidates = new ArrayList<>();
		for (VersionQuestions version : versions) {
			for (Question question : version.questions()) {
				List<Located> same = locate(question.questionNote(), versions);
				boolean oneQuestion = same.stream().map(at -> at.question().questionId()).distinct().count() == 1
						&& same.stream().map(Located::formVersion).distinct().count() == same.size();
				if (oneQuestion && sortable(same)) {
					candidates.add(new Located(version.formVersion(), question));
				}
			}
		}
		return List.copyOf(candidates);
	}

	// ids는 일치 응답의 response_id 오름차순이다. 값 순(order), 같으면 response_id 오름차순, 빈 값·문항 없는 버전은 맨 뒤.
	List<String> sort(List<String> ids, String order) {
		Map<String, ResponseRecord> byId = records.stream()
			.collect(Collectors.toMap(ResponseRecord::responseId, Function.identity()));
		Map<Integer, Located> byVersion = byVersion();
		List<Keyed> present = new ArrayList<>();
		List<String> last = new ArrayList<>();
		for (String id : ids) {
			ResponseRecord record = byId.get(id);
			Located at = byVersion.get(record.formVersion());
			Comparable<?> key = at == null ? null : key(at, value(record, at));
			if (key == null) {
				last.add(id);
			}
			else {
				present.add(new Keyed(id, key));
			}
		}
		Comparator<Keyed> byValue = Comparator.comparing(Keyed::key, ListSort::compareKeys);
		if ("desc".equals(order)) {
			byValue = byValue.reversed();
		}
		present.sort(byValue.thenComparing(Keyed::id));
		last.sort(Comparator.naturalOrder());
		List<String> sorted = new ArrayList<>(present.stream().map(Keyed::id).toList());
		sorted.addAll(last);
		return List.copyOf(sorted);
	}

	// 목록에 응답이 있는데 정렬 문항이 없는 버전(사람 결정 2026-11-08). 없으면 null이라 키를 내지 않는다.
	List<Integer> unavailableVersions(List<String> ids) {
		Map<Integer, Located> byVersion = byVersion();
		TreeSet<Integer> versions = new TreeSet<>();
		Map<String, Integer> versionOf = records.stream()
			.collect(Collectors.toMap(ResponseRecord::responseId, ResponseRecord::formVersion));
		ids.stream().map(versionOf::get).filter(version -> !byVersion.containsKey(version)).forEach(versions::add);
		return versions.isEmpty() ? null : List.copyOf(versions);
	}

	private Map<Integer, Located> byVersion() {
		Map<Integer, Located> byVersion = new TreeMap<>();
		located.forEach(at -> byVersion.put(at.formVersion(), at));
		return byVersion;
	}

	private boolean sortable(List<Located> questions) {
		if (questions.isEmpty() || questions.stream().map(at -> at.question().questionType()).distinct().count() != 1) {
			return false;
		}
		Question first = questions.get(0).question();
		if (first.isSingleChoice()) {
			return questions.stream().map(at -> at.question().options()).distinct().count() == 1;
		}
		if (!"short_answer".equals(first.questionType())
				|| questions.stream().map(this::isDate).distinct().count() != 1) {
			return false;
		}
		return questions.stream()
			.allMatch(at -> records.stream()
				.filter(record -> record.formVersion() == at.formVersion())
				.map(record -> value(record, at))
				.filter(Objects::nonNull)
				.allMatch(value -> key(at, value) != null));
	}

	private boolean isDate(Located at) {
		FormDefinition form = forms.get(at.formVersion());
		return form != null && form.questions()
			.stream()
			.filter(question -> question.questionId().equals(at.question().questionId()))
			.anyMatch(question -> "date".equals(question.inputFormatKind()));
	}

	private static String value(ResponseRecord record, Located at) {
		List<String> values = record.answers().getOrDefault(at.question().questionId(), List.of());
		return values.isEmpty() || values.get(0).isBlank() ? null : values.get(0).strip();
	}

	// 정렬 키. 값이 없거나 형식에 맞지 않으면 null이다.
	private Comparable<?> key(Located at, String value) {
		if (value == null) {
			return null;
		}
		if (at.question().isSingleChoice()) {
			int index = at.question().options().indexOf(value);
			return index < 0 ? null : index;
		}
		if (isDate(at)) {
			if (!DATE.matcher(value).matches()) {
				return null;
			}
			try {
				return LocalDate.parse(value);
			}
			catch (DateTimeParseException e) {
				return null;
			}
		}
		return NUMBER.matcher(value).matches() ? new BigDecimal(value) : null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int compareKeys(Comparable left, Comparable right) {
		return left.compareTo(right);
	}

	private static List<Located> locate(String by, List<VersionQuestions> versions) {
		List<Located> located = new ArrayList<>();
		for (VersionQuestions version : versions) {
			LiteralMatcher.questionsByNote(by, version)
				.forEach(question -> located.add(new Located(version.formVersion(), question)));
		}
		return List.copyOf(located);
	}

	private record Keyed(String id, Comparable<?> key) {
	}

}
