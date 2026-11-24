package com.opinio.passform.service.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;

// summarize의 target이 가리키는 문항(SPEC 319·338행). 요약 대상 판정(ClarifyRules)과 요약 실행(SummaryService)이 함께 쓴다.
// 문구가 완전히 같은 문항이 어느 버전에든 있으면 그 문항만 쓴다. 없을 때만 target 안에 낱말 경계로 들어 있는 문항 문구를 찾는다.
// 포함으로 찾았으면 target에서 문항 문구를 뺀 나머지를 본다: 조사·일반어만 남으면 그 문항이다. 그 밖의 낱말(선택지 포함)이
// 남으면 문항을 찾지 못한 것으로 본다(unknown_condition). 남은 말을 조건으로 되살리지 않는다.
final class TargetMatching {

	private TargetMatching() {
	}

	// notes는 target이 가리키는 문항 문구다. 비어 있으면 찾지 못한 것이다.
	record Resolution(Set<String> notes) {

		static final Resolution NONE = new Resolution(Set.of());

		boolean isTarget(Question question) {
			return notes.contains(question.questionNote());
		}

	}

	static Resolution resolve(String target, List<VersionQuestions> versions) {
		if (target == null || target.isBlank()) {
			return Resolution.NONE;
		}
		String text = target.strip();
		List<Question> questions = versions.stream().flatMap(version -> version.questions().stream()).toList();
		if (questions.stream().anyMatch(question -> question.questionNote().equals(text))) {
			return new Resolution(Set.of(text));
		}
		List<QueryText.Found> found = QueryText.longestOnly(questions.stream()
			.map(Question::questionNote)
			.filter(note -> !note.isBlank())
			.distinct()
			.flatMap(note -> QueryText.boundedSpans(text, note).stream().map(span -> new QueryText.Found(note, span)))
			.toList());
		if (found.isEmpty()) {
			return Resolution.NONE;
		}
		Set<String> notes = new LinkedHashSet<>();
		found.forEach(entry -> notes.add(entry.phrase()));
		// 요약할 수 없는 문항이면 되묻기 표(unsummarizable_field)가 판정한다. 나머지는 보지 않는다.
		if (questions.stream().filter(question -> notes.contains(question.questionNote())).anyMatch(question -> !question.isSummarizable())) {
			return new Resolution(Set.copyOf(notes));
		}
		// 문항 문구 말고 조사·일반어만 남아야 한다. 여러 문항 문구 사이에 다른 말이 끼어도 같다.
		StringBuilder rest = new StringBuilder(text);
		found.forEach(entry -> QueryText.blank(rest, entry.span()));
		return QueryText.onlyParticlesAndGeneric(rest.toString(), versions) ? new Resolution(Set.copyOf(notes)) : Resolution.NONE;
	}

	// target이 "선택지 + 문항 문구"로 된 꼴(파서가 조건을 target에 섞은 꼴)일 때 그 문항 문구와 선택지.
	// optionsBeforeNote는 모든 선택지가 문항 문구보다 앞에 있는가다.
	record Lift(String note, List<String> options, boolean optionsBeforeNote) {
	}

	// 완전 일치 문항이 있거나, 문항 문구가 하나로 정해지지 않거나, 요약할 수 없는 문항이거나, 남은 말에 선택지·조사·일반어
	// 말고 다른 말이 있으면 비어 있다. 판정만 하고 조건을 더하지는 않는다(AnalysisService.liftTargetOption이 정한다).
	static Optional<Lift> lift(String target, List<VersionQuestions> versions) {
		if (target == null || target.isBlank()) {
			return Optional.empty();
		}
		String text = target.strip();
		List<Question> questions = versions.stream().flatMap(version -> version.questions().stream()).toList();
		if (questions.stream().anyMatch(question -> question.questionNote().equals(text))) {
			return Optional.empty();
		}
		List<QueryText.Found> notes = QueryText.longestOnly(questions.stream()
			.map(Question::questionNote)
			.filter(note -> !note.isBlank())
			.distinct()
			.flatMap(note -> QueryText.boundedSpans(text, note).stream().map(span -> new QueryText.Found(note, span)))
			.toList());
		if (notes.size() != 1) {
			return Optional.empty();
		}
		QueryText.Found note = notes.get(0);
		if (questions.stream().filter(question -> question.questionNote().equals(note.phrase())).anyMatch(question -> !question.isSummarizable())) {
			return Optional.empty();
		}
		StringBuilder rest = new StringBuilder(text);
		QueryText.blank(rest, note.span());
		List<QueryText.Found> options = QueryText.longestOnly(QueryText.choiceOptionsOf(versions)
			.stream()
			// 일반어와 글자가 같은 선택지("학생", "지원자")는 꺼내지 않는다. 전체를 뜻했을 수 있어 되묻기로 보낸다(SPEC 338행).
			.filter(option -> !option.isBlank() && !QueryText.isGenericWord(option))
			.flatMap(option -> QueryText.boundedSpans(rest.toString(), option).stream().map(span -> new QueryText.Found(option, span)))
			.toList());
		if (options.isEmpty()) {
			return Optional.empty();
		}
		options.forEach(option -> QueryText.blank(rest, option.span()));
		if (!QueryText.onlyParticlesAndGeneric(rest.toString(), versions)) {
			return Optional.empty();
		}
		List<String> phrases = options.stream().map(QueryText.Found::phrase).distinct().toList();
		boolean before = options.stream().allMatch(option -> option.span()[1] <= note.span()[0]);
		return Optional.of(new Lift(note.phrase(), phrases, before));
	}

}
