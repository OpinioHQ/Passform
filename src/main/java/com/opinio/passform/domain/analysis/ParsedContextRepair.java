package com.opinio.passform.domain.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 실제 파서(gpt-6-luna) 출력의 반복 오류를 코드로 보정한다. SPEC에 없는 단계라 사람이 확정해야 한다(docs/demo/NOTES.md).
// 원칙: 파서가 버린 표현이 전체 건수로 나가지 않게 한다. 보정이 지나쳐도 결과는 틀린 숫자가 아니라 되묻기가 된다.
public final class ParsedContextRepair {

	private static final String PEOPLE = "(?:학생|학생들|사람|사람들|응답|응답자|응답자들|지원자|지원자들|신청자|신청자들|인원|분들)";

	private static final Pattern TRAILING_NOUN = Pattern.compile("^(.+?)\\s+" + PEOPLE + "$");

	private static final Pattern NEGATED = Pattern
		.compile("^(.+?)(?:이|가|을|를)?\\s*(?:아닌|아님|아니고|아니며|제외한|제외|빼고|말고)(?:\\s*" + PEOPLE + ")?$");

	private static final Pattern UNANSWERED = Pattern
		.compile("^(.+?)(?:에|을|를)?\\s*(?:답하지 않은|응답하지 않은|작성하지 않은|안 쓴|안 한|비운)(?:\\s*" + PEOPLE + ")?$");

	private static final Pattern ANSWERED = Pattern
		.compile("^(.+?)(?:에|을|를)?\\s*(?:답한|응답한|작성한)(?:\\s*" + PEOPLE + ")?$");

	private static final Pattern COUNT_CUE = Pattern
		.compile("(^|\\s)(수|인원|인원수|몇\\s*명|몇\\s*개|몇\\s*건|명수|건수)(를|은|는|가|이야|야)?(\\s|\\?|$)");

	private static final Pattern PARTICLE = Pattern.compile("(은|는|이|가|을|를|의|에서|에|로|으로|도|만|들)$");

	private static final Pattern PUNCTUATION = Pattern.compile("[?!.,~·\"'()]");

	private static final Set<String> GENERIC = Set.of("전체", "모든", "모두", "전부", "총", "응답", "응답자", "응답자들", "응답수", "결과",
			"설문", "설문조사", "폼", "학생", "학생들", "사람", "사람들", "지원자", "지원자들", "신청자", "신청자들", "인원", "인원수", "수", "몇", "명", "개", "건",
			"몇명", "몇개", "몇건", "개수", "명수", "건수", "명이야", "개야", "건이야", "이야", "야", "좀", "제", "알려줘", "알려주세요", "알려", "보여줘", "보여주세요",
			"보여", "말해줘", "세어줘", "해줘", "주세요", "줘", "요약", "요약해줘", "요약해주세요", "정리", "정리해줘", "정리해주세요", "설명", "설명해줘", "목록", "리스트",
			"나열", "나열해줘", "중", "중에서", "중에", "있어", "있나요", "돼", "되나요", "의견", "순", "순으로", "정렬", "정렬해줘", "기준", "내림차순", "오름차순",
			"많은", "적은", "높은", "낮은", "어린", "먼저", "차례", "대로", "그리고", "또는", "이거나", "이면서", "이고", "아닌", "제외", "제외한", "빼고", "말고",
			"가장", "제일", "나온", "많이", "들어온", "지금까지", "현재", "받은", "제출", "제출한", "제출된", "답하지", "답한", "응답한", "응답하지", "않은", "안", "한",
			"답변", "미응답", "작성한", "작성하지", "대한", "관련", "해서", "대해", "대해서");

	private ParsedContextRepair() {
	}

	// options: 폼의 모든 선택지 문구. isQuestion: 표현이 어느 버전의 문항을 가리키는지.
	public static RequestContext repair(String query, RequestContext parsed, Set<String> options,
			Predicate<String> isQuestion) {
		String target = blankIfGeneric(parsed.target());
		String topic = blankIfGeneric(parsed.topic());
		if (target != null && target.equals(topic)) {
			// 파서가 같은 표현을 두 칸에 복사한다. 문항을 가리키면 target, 아니면 의견 주제다.
			if (isQuestion.test(target)) {
				topic = null;
			}
			else {
				target = null;
			}
		}
		// "비용이 부담된다는 의견" → "비용이 부담된다"
		topic = (topic != null) ? topic.replaceFirst("(?:라는|는)?\\s*의견$", "").trim() : null;
		if (topic != null && topic.isEmpty()) {
			topic = null;
		}
		// 의견 주제가 있는데 target이 문항을 가리키지 않으면 파서가 표현을 잘못 넣은 것이다.
		if (topic != null && target != null && !isQuestion.test(target)) {
			target = null;
		}

		List<String> rewritten = new ArrayList<>();
		List<String> originals = new ArrayList<>();
		int negated = 0;
		for (String raw : parsed.conditions()) {
			String condition = raw.trim();
			if (condition.equals(parsed.target()) || condition.equals(parsed.topic())) {
				continue;
			}
			originals.add(condition);
			if (condition.contains(" - ")) {
				rewritten.add(condition);
				continue;
			}
			Matcher unanswered = UNANSWERED.matcher(condition);
			if (unanswered.matches()) {
				rewritten.add(unanswered.group(1).trim() + " - 미응답");
				continue;
			}
			Matcher answered = ANSWERED.matcher(condition);
			if (answered.matches()) {
				rewritten.add(answered.group(1).trim() + " - 응답");
				continue;
			}
			Matcher negation = NEGATED.matcher(condition);
			if (negation.matches()) {
				negated++;
				rewritten.add(negation.group(1).trim());
				continue;
			}
			rewritten.addAll(splitByOptions(withoutTrailingNoun(condition), options));
		}
		String match = parsed.match();
		List<String> conditions = rewritten;
		if (negated > 0 && negated < originals.size()) {
			// 긍정·부정이 섞이면 단일 match로 표현할 수 없다. 뜻을 바꾸지 않게 원문 그대로 두고 비운다(AC26).
			conditions = originals;
			match = null;
		}
		else if (negated > 0) {
			match = "not";
		}
		List<String> withDropped = new ArrayList<>(conditions);
		withDropped.addAll(droppedTerms(query, conditions, target, topic, parsed));
		if (withDropped.size() <= 1 && !"not".equals(match)) {
			match = null;
		}
		String operation = parsed.operation();
		if (operation == null && COUNT_CUE.matcher(query).find()) {
			operation = RequestContext.COUNT;
		}
		return new RequestContext(withDropped, match, operation, parsed.sort(), target, topic);
	}

	private static String blankIfGeneric(String phrase) {
		if (phrase == null) {
			return null;
		}
		String trimmed = phrase.trim();
		for (String word : trimmed.split("\\s+")) {
			if (!isGeneric(word)) {
				return trimmed;
			}
		}
		return null;
	}

	private static boolean isGeneric(String word) {
		return GENERIC.contains(word) || GENERIC.contains(PARTICLE.matcher(word).replaceAll(""));
	}

	private static String withoutTrailingNoun(String condition) {
		Matcher trailing = TRAILING_NOUN.matcher(condition);
		return trailing.matches() ? trailing.group(1).trim() : condition;
	}

	// "소프트웨어학과 4학년"처럼 낱말이 전부 선택지면 조건을 나눈다. 하나라도 아니면 그대로 둔다.
	private static List<String> splitByOptions(String condition, Set<String> options) {
		String[] words = condition.split("\\s+");
		if (words.length < 2) {
			return List.of(condition);
		}
		List<String> split = new ArrayList<>();
		for (String word : words) {
			String stem = options.contains(word) ? word : PARTICLE.matcher(word).replaceAll("");
			if (!options.contains(stem)) {
				return List.of(condition);
			}
			split.add(stem);
		}
		return split;
	}

	// 원문의 낱말 중 일반어도 아니고 파서 출력 어디에도 없는 것을 찾는다. 이런 낱말은 조건으로 되살려 매칭 단계에 맡긴다.
	private static List<String> droppedTerms(String query, List<String> conditions, String target, String topic,
			RequestContext parsed) {
		List<String> covered = new ArrayList<>(conditions);
		covered.addAll(parsed.conditions());
		for (String phrase : new String[] { target, topic, parsed.topic(), parsed.target() }) {
			if (phrase != null) {
				covered.add(phrase);
			}
		}
		if (parsed.sort() != null) {
			covered.add(parsed.sort().by());
		}
		List<String> dropped = new ArrayList<>();
		for (String raw : PUNCTUATION.matcher(query).replaceAll(" ").trim().split("\\s+")) {
			String token = PARTICLE.matcher(raw).replaceAll("");
			if (raw.isEmpty() || token.isEmpty() || isGeneric(raw) || isCovered(raw, token, covered)
					|| dropped.contains(token)) {
				continue;
			}
			dropped.add(token);
		}
		return dropped;
	}

	private static boolean isCovered(String raw, String token, List<String> covered) {
		for (String phrase : covered) {
			for (String word : phrase.split("\\s+")) {
				String stem = PARTICLE.matcher(word).replaceAll("");
				if (!stem.isEmpty() && (raw.startsWith(stem) || stem.startsWith(token) || word.equals(raw))) {
					return true;
				}
			}
		}
		return false;
	}

}
