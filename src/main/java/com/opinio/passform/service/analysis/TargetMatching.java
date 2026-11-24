package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;

// summarize의 target이 가리키는 문항(SPEC 319·338행). 요약 대상 판정(ClarifyRules)과 요약 실행(SummaryService)이 함께 쓴다.
// 문구가 완전히 같은 문항이 어느 버전에든 있으면 그 문항만 쓴다. 없을 때만 target 안에 낱말 경계로 들어 있는 문항 문구를 찾는다.
// 포함으로 찾았으면 target에서 문항 문구를 뺀 나머지를 본다: 일반어·조사만 남으면 그 문항, 선택지 문구가 남으면 그 문구를
// 조건으로 되살리고, 그 밖의 낱말이 남으면 문항을 찾지 못한 것으로 본다(unknown_condition).
final class TargetMatching {

	private TargetMatching() {
	}

	// notes는 target이 가리키는 문항 문구(비어 있으면 찾지 못함), options는 나머지에서 되살릴 선택지 문구다.
	record Resolution(Set<String> notes, List<String> options) {

		static final Resolution NONE = new Resolution(Set.of(), List.of());

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
			return new Resolution(Set.of(text), List.of());
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
		StringBuilder rest = new StringBuilder(text);
		found.forEach(entry -> QueryText.blank(rest, entry.span()));
		// 여러 문항 문구를 이은 target은 그 사이에 조사·일반어만 있어야 한다. 다른 낱말(선택지 포함)이 끼면 그 낱말이
		// 조건인지 확정할 수 없으므로 문항을 찾지 못한 것으로 본다("지원 동기와 홍보 참여 동기"). 남은 것이 조사뿐이면 되묻기로 간다.
		if (notes.size() > 1) {
			return QueryText.onlyParticlesAndGeneric(rest.toString()) ? new Resolution(Set.copyOf(notes), List.of())
					: Resolution.NONE;
		}
		// 요약할 수 없는 문항이면 되묻기로 가므로 나머지는 보지 않는다.
		if (questions.stream().filter(question -> notes.contains(question.questionNote())).anyMatch(question -> !question.isSummarizable())) {
			return new Resolution(Set.copyOf(notes), List.of());
		}
		List<String> options = new ArrayList<>();
		if (!QueryText.onlyOptionsAndGeneric(rest.toString(), versions, options)) {
			return Resolution.NONE;
		}
		return new Resolution(Set.copyOf(notes), List.copyOf(options));
	}

}
