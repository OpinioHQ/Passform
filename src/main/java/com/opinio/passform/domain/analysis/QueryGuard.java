package com.opinio.passform.domain.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 원문 규칙 검사. 범위 표현은 파서 전에, 통계 표현은 파서 뒤 3순위로 적용한다(SPEC 5절 338, 340행).
// 찾은 표현이 폼의 문항·선택지 문구에 있으면("통계학과", "앱 버전") 막지 않는다.
// 한계: 명시적인 표현만 잡는다. 놓친 표현은 파서와 매처로 넘어간다.
public final class QueryGuard {

	private static final List<Pattern> SCOPE = List.of(
			Pattern.compile("(?i)(버전|version|ver\\.?)\\s*\\d+"),
			Pattern.compile("(?i)(?<![a-z])v\\s*\\d+"),
			Pattern.compile("\\d+\\s*(차\\s*)?버전"),
			Pattern.compile("버전\\s*(별|마다|끼리|사이)"),
			Pattern.compile("(최신|이전|옛|구|첫|마지막|현재|지난)\\s*버전"));

	private static final Pattern STATISTIC = Pattern
		.compile("평균|비율|퍼센트|백분율|%|중앙값|최댓값|최솟값|최대값|최소값|합계|표준편차|분포|통계");

	private static final Pattern GROUP_BY = Pattern
		.compile("([가-힣A-Za-z0-9]+별)(?=로|\\s|$|통계|인원|응답|수|몇|비교|현황)");

	private static final Set<String> NOT_GROUPING = Set.of("특별", "개별", "차별", "구별", "식별", "선별", "작별", "이별", "분별",
			"판별", "유별", "각별");

	private static final Pattern NEGATION = Pattern.compile("아닌|아니|제외|빼고|말고");

	private QueryGuard() {
	}

	public static boolean mentionsUnsupportedScope(String query, Collection<String> formTexts) {
		return SCOPE.stream().anyMatch(pattern -> hasUnlistedMatch(pattern, 0, query, formTexts));
	}

	public static boolean mentionsUnsupportedStatistic(String query, Collection<String> formTexts) {
		if (hasUnlistedMatch(STATISTIC, 0, query, formTexts)) {
			return true;
		}
		Matcher matcher = GROUP_BY.matcher(query);
		while (matcher.find()) {
			String phrase = matcher.group(1);
			if (!NOT_GROUPING.contains(phrase) && !isInForm(phrase, formTexts)) {
				return true;
			}
		}
		return false;
	}

	public static boolean mentionsNegation(String query) {
		return NEGATION.matcher(query).find();
	}

	private static boolean hasUnlistedMatch(Pattern pattern, int group, String query, Collection<String> formTexts) {
		Matcher matcher = pattern.matcher(query);
		while (matcher.find()) {
			if (!isInForm(matcher.group(group), formTexts)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInForm(String phrase, Collection<String> formTexts) {
		return formTexts.stream().anyMatch(text -> text != null && text.contains(phrase));
	}

}
