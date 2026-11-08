package com.opinio.passform.domain.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;

// 글자가 같은 조건만 한 버전의 문항·선택지에 연결한다(docs/api/analyze_api.md 결정 A9). LLM 없이 코드로 푼다.
// 빈 목록은 "이 버전에서 글자로 풀지 못함"이고 매칭 불가가 아니다. 줄임말·번호·별칭은 다음 단계(LLM 매처)가 본다.
public final class LiteralMatcher {

	// match_conditions.md 매칭 규칙 3의 응답 여부 표현.
	private static final Set<String> ANSWERED = Set.of("응답", "답함", "작성함");

	private static final Set<String> UNANSWERED = Set.of("미응답", "안 씀", "비움");

	private LiteralMatcher() {
	}

	// 1) 조건 글자가 선택형 문항의 선택지와 같으면 option. 2) "문항 문구 - 값"이면 문구로 문항을 정하고 값으로 종류를 정한다.
	// 같은 글자가 여러 문항에 걸리면 모두 돌려준다. 고르지 않고 되묻기 표 7행에 맡긴다.
	public static List<ConditionMatch> match(String condition, VersionQuestions version) {
		String phrase = condition.strip();
		List<ConditionMatch> matches = new ArrayList<>();
		for (Question question : version.questions()) {
			if (question.isChoice() && question.options().contains(phrase)) {
				matches.add(new ConditionMatch(condition, version.formVersion(), question.questionId(), MatchKind.OPTION, phrase));
			}
		}
		if (!matches.isEmpty()) {
			return List.copyOf(matches);
		}
		int dash = phrase.lastIndexOf('-');
		if (dash <= 0) {
			return List.of();
		}
		String value = phrase.substring(dash + 1).strip();
		for (Question question : questionsByNote(phrase.substring(0, dash), version)) {
			if (ANSWERED.contains(value)) {
				matches.add(new ConditionMatch(condition, version.formVersion(), question.questionId(), MatchKind.ANSWERED, null));
			}
			else if (UNANSWERED.contains(value)) {
				matches.add(new ConditionMatch(condition, version.formVersion(), question.questionId(), MatchKind.UNANSWERED, null));
			}
			else if (question.isChoice() && question.options().contains(value)) {
				matches.add(new ConditionMatch(condition, version.formVersion(), question.questionId(), MatchKind.OPTION, value));
			}
		}
		return List.copyOf(matches);
	}

	// 문항 문구가 글자 그대로 같은 문항. 조건의 문항 부분과 list의 sort.by가 함께 쓴다.
	public static List<Question> questionsByNote(String phrase, VersionQuestions version) {
		String note = phrase.strip();
		if (note.isEmpty()) {
			return List.of();
		}
		return version.questions().stream().filter(question -> question.questionNote().equals(note)).toList();
	}

	// 다음 단계가 돌려준 항목이 그 버전의 실제 문항·선택지인지 본다. 허용 값 밖이면 버린다(AGENTS 5절).
	public static boolean allowed(ConditionMatch entry, VersionQuestions version) {
		if (entry.formVersion() != version.formVersion()) {
			return false;
		}
		return version.questions()
			.stream()
			.filter(question -> question.questionId().equals(entry.questionId()))
			.anyMatch(question -> switch (entry.kind()) {
				case OPTION -> question.isChoice() && question.options().contains(entry.option());
				case ANSWERED, UNANSWERED -> entry.option() == null;
			});
	}

}
