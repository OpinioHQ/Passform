package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.opinio.passform.domain.form.VersionQuestions;

// 제작자의 원문을 코드로 판정한다(SPEC 334·338·340·342행). {query}에만 쓴다. {context}에는 원문이 없다.
// 낱말 목록 기반이라 명시적 표현만 잡는다(시험 데모 3-4절). 애매하면 되묻는 쪽으로 기운다. 틀린 숫자보다 낫다.
final class QueryText {

	// 표 1행. 파서에 보내기 전에 본다. 버전을 고르거나 비교하는 표현만 잡는다.
	private static final Pattern VERSION_SCOPE = Pattern.compile("버전\\s*\\d+" + "|\\d+\\s*번?째?\\s*버전"
			+ "|(?<![a-z0-9])v\\d+(?![a-z0-9])" + "|버전\\s*(별|마다|끼리|간|사이|비교)"
			+ "|(이전|예전|옛|구|최신|새|현재|지난|첫|첫번째|마지막|특정|각)\\s*버전", Pattern.CASE_INSENSITIVE);

	// 표 3행. 평균·비율·문항별 통계처럼 지원하지 않는 동작.
	private static final Pattern STATISTICS = Pattern
		.compile("평균|비율|퍼센트|백분율|통계|중앙값|표준편차|합계|총합|분포|최댓값|최솟값|최대값|최소값");

	private static final Pattern GROUP_BY = Pattern.compile("^[가-힣A-Za-z0-9]+(별|별로|별로는|마다)$");

	private static final Set<String> NOT_GROUP_BY = Set.of("개별", "특별", "각별", "차별", "구별", "식별");

	private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$");

	// 알려진 동작어만 꼬리에서 뗀다. 모르는 "~줘"는 조건으로 남겨 되묻는다(SPEC 334행).
	private static final Pattern ACTION_TAIL = Pattern.compile("(알려|보여|세어|정리해|요약해|나열해|찾아|뽑아)(줘|주세요|줄래요|줄래|주라)$");

	private static final Pattern TRAILING_PARTICLE = Pattern.compile("(들|을|를|은|는|이|가|의|도|만|에)$");

	// 동작어와 일반어. 이것만 남으면 조건이 없는 요청이다.
	private static final Set<String> GENERIC = Set.of("전체", "전부", "모든", "모두", "총", "다", "응답", "응답자", "응답수", "제출", "제출자",
			"결과", "학생", "지원자", "신청자", "참여자", "사람", "인원", "인원수", "수", "명", "명수", "명이야", "명인가요", "건", "건수", "개", "개수", "중",
			"중에서", "의견", "내용", "답변", "답", "목록", "리스트", "명단", "요약", "정리", "나열", "정렬", "알려", "보여", "폼", "설문", "설문조사");

	private static final List<String> NEGATIONS = List.of("아닌", "아니고", "아닌데", "제외", "제외한", "제외하고", "빼고", "뺀", "말고");

	private QueryText() {
	}

	static boolean asksVersionScope(String query) {
		return VERSION_SCOPE.matcher(query).find();
	}

	// 응답이 남은 버전의 문항 문구나 선택지 안에 있는 표현("평균 이상")은 통계 요청으로 세지 않는다.
	static boolean asksStatistics(String query, List<VersionQuestions> versions) {
		List<String> formTexts = versions.stream()
			.flatMap(version -> version.questions().stream())
			.flatMap(question -> Stream.concat(Stream.of(question.questionNote()), question.options().stream()))
			.toList();
		Predicate<String> outsideForm = expression -> formTexts.stream().noneMatch(text -> text.contains(expression));
		Matcher statistics = STATISTICS.matcher(query);
		while (statistics.find()) {
			if (outsideForm.test(statistics.group())) {
				return true;
			}
		}
		return tokens(query).stream()
			.anyMatch(token -> GROUP_BY.matcher(token).matches() && !NOT_GROUP_BY.contains(token) && outsideForm.test(token));
	}

	// 표 5행 혼합 부정: 부정 표현이 있는데 부정된 낱말 말고 다른 조건 표현도 남아 있다.
	static boolean mixesNegation(String query, RequestContext context) {
		List<String> words = wordsWithoutAction(query);
		List<String> positive = new ArrayList<>();
		boolean negated = false;
		for (int index = 0; index < words.size(); index++) {
			String word = words.get(index);
			if (isNegation(word)) {
				negated = true;
				continue;
			}
			boolean beforeBareNegation = index + 1 < words.size() && NEGATIONS.contains(words.get(index + 1));
			if (!beforeBareNegation && isConditionWord(word, context)) {
				positive.add(word);
			}
		}
		return negated && !positive.isEmpty() && !withinSingleNegatedCondition(positive, context);
	}

	// "소프트웨어 학과가 아닌"처럼 여러 낱말로 된 조건 하나를 부정하면 혼합이 아니다(SPEC 342행).
	private static boolean withinSingleNegatedCondition(List<String> positive, RequestContext context) {
		return "not".equals(context.match()) && context.conditions().size() == 1
				&& positive.stream().allMatch(context.conditions().get(0)::contains);
	}

	// SPEC 334행: 파서가 조건을 버린 출력을 전체 건수로 내보내지 않는다. 남는 표현을 되살려 매칭으로 보낸다.
	// 부정 표현이 있으면 낱말로 쪼개지 않고 한 덩어리로 둔다. 매칭되지 않아 되묻게 된다.
	static RequestContext reviveDroppedConditions(String query, RequestContext parsed) {
		if (!parsed.conditions().isEmpty()) {
			return parsed;
		}
		List<String> allWords = wordsWithoutAction(query);
		List<String> words = allWords.stream().filter(word -> isConditionWord(word, parsed)).toList();
		if (words.isEmpty()) {
			return parsed;
		}
		boolean negated = allWords.stream().anyMatch(QueryText::isNegation);
		return parsed.withRevivedConditions(negated ? List.of(String.join(" ", words)) : words);
	}

	private static List<String> wordsWithoutAction(String query) {
		return tokens(query).stream()
			.map(token -> ACTION_TAIL.matcher(token).replaceFirst(""))
			.filter(word -> !word.isEmpty())
			.toList();
	}

	private static boolean isConditionWord(String token, RequestContext context) {
		return !isGeneric(token) && !coveredByParsedKeys(token, context);
	}

	private static boolean isGeneric(String token) {
		if (token.startsWith("몇") || GENERIC.contains(token)) {
			return true;
		}
		String stem = token;
		for (int count = 0; count < 2; count++) {
			stem = TRAILING_PARTICLE.matcher(stem).replaceFirst("");
			if (GENERIC.contains(stem)) {
				return true;
			}
		}
		return false;
	}

	// 그 동작에서 실제로 쓰이는 키에 담긴 표현만 버린 것이 아니다. 쓰이지 않는 키는 곧 지워진다(SPEC 311·312행).
	private static boolean coveredByParsedKeys(String token, RequestContext context) {
		boolean summarize = RequestContext.SUMMARIZE.equals(context.operation());
		boolean list = RequestContext.LIST.equals(context.operation());
		String sortBy = list && context.sort() != null ? context.sort().by() : null;
		return Stream.of(summarize ? context.target() : null, summarize ? context.topic() : null, sortBy)
			.filter(Objects::nonNull)
			.flatMap(phrase -> tokens(phrase).stream())
			.anyMatch(word -> token.contains(word) || word.contains(token));
	}

	private static boolean isNegation(String token) {
		return NEGATIONS.stream().anyMatch(token::endsWith);
	}

	private static List<String> tokens(String text) {
		return Arrays.stream(text.strip().split("\\s+"))
			.map(token -> EDGE_PUNCTUATION.matcher(token).replaceAll(""))
			.filter(token -> !token.isEmpty())
			.toList();
	}

}
