package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.opinio.passform.domain.analysis.LiteralMatcher;
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

	private static final Pattern WORD_BREAK = Pattern.compile("[\\p{P}\\p{S}]");

	// 알려진 동작어만 꼬리에서 뗀다. 모르는 "~줘"는 조건으로 남겨 되묻는다(SPEC 334행).
	private static final Pattern ACTION_TAIL = Pattern.compile("(알려|보여|세어|정리해|요약해|나열해|찾아|뽑아)(줘|주세요|줄래요|줄래|주라)$");

	// 묻는 끝말("의견있어", "있나요")도 동작어처럼 떼어 남는 낱말만 본다.
	private static final Pattern EXIST_TAIL = Pattern.compile("있(어요|어|나요|나|니|는지|을까요|을까)$");

	// 선택지 문구 바로 뒤에 붙어도 낱말 경계로 보는 조사·어미("3학년인", "팀장이고").
	private static final Set<String> OPTION_PARTICLES = Set.of("이", "가", "은", "는", "을", "를", "인", "의", "만", "도", "와", "과",
			"랑", "이랑", "에", "로", "으로", "에서", "라고", "이라고", "인데", "이고", "고", "이며", "이거나", "거나", "이나", "나", "인지", "이면",
			"면", "인가", "들");

	private static final Pattern TRAILING_PARTICLE =Pattern.compile("(들|을|를|은|는|이|가|의|도|만|에)$");

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
	// 문항 문구 낱말("맡은 역할이")은 조건이 아니므로 긍정 조건으로 세지 않는다(골든 question_wording_not_dropped_condition과 같은 규칙).
	// 부정어는 문항 문구("제외 사유")에 들어 있어도 부정으로 센다(SPEC 342행, AC26).
	static boolean mixesNegation(String query, RequestContext context, List<VersionQuestions> versions) {
		List<String> words = wordsWithoutAction(query).stream()
			.filter(word -> isNegation(word) || !isQuestionWording(word, versions))
			.toList();
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
	// target·topic이 있는 요약은 남은 낱말이 대개 의견 표현의 일부다("의견있어"). 부정 표현이 없고, 남는 조건 낱말이 모두
	// 원문에 글자 그대로 있는 선택지 문구로 설명될 때만 그 선택지 문구만 되살린다("3학년 학생들의 걱정되는 점").
	// 하나라도 설명되지 않으면("3학년생", "3학년 아닌") 예전대로 낱말을 되살려 되묻는다. 조건 빠진 요약을 만들지 않는다.
	// count·list·동작 없음은 그대로 낱말을 되살린다(조건 빠진 숫자를 내보내지 않는다).
	static RequestContext reviveDroppedConditions(String query, RequestContext parsed) {
		return reviveDroppedConditions(query, parsed, List.of());
	}

	static RequestContext reviveDroppedConditions(String query, RequestContext parsed, List<VersionQuestions> versions) {
		if (!parsed.conditions().isEmpty()) {
			return parsed;
		}
		List<String> allWords = wordsWithoutAction(query);
		List<String> words = allWords.stream().filter(word -> isConditionWord(word, parsed)).toList();
		if (words.isEmpty()) {
			return parsed;
		}
		boolean negated = allWords.stream().anyMatch(QueryText::isNegation);
		if (!negated && RequestContext.SUMMARIZE.equals(parsed.operation())
				&& usedPhrases(parsed).anyMatch(phrase -> !phrase.isBlank())) {
			List<String> options = droppedOptions(query, parsed, versions);
			if (!options.isEmpty() && conditionWordsOutside(query, options, parsed).isEmpty()) {
				return parsed.withRevivedConditions(options);
			}
		}
		return parsed.withRevivedConditions(negated ? List.of(String.join(" ", words)) : words);
	}

	// 선택지 문구가 낱말 경계로 나온 자리를 지운 뒤에도 남는 조건 낱말. 선택지 바로 뒤에 붙었던 조사("3학년인"의 "인")는 뺀다.
	private static List<String> conditionWordsOutside(String query, List<String> options, RequestContext parsed) {
		StringBuilder rest = new StringBuilder(query);
		for (String option : options) {
			for (int[] span : spans(query, option, true)) {
				for (int index = span[0]; index < span[1]; index++) {
					rest.setCharAt(index, ' ');
				}
			}
		}
		return wordsWithoutAction(rest.toString()).stream()
			.filter(word -> !OPTION_PARTICLES.contains(word) && isConditionWord(word, parsed))
			.toList();
	}

	// SPEC 334행: 파서가 조건 일부를 버렸으면 원문에 글자 그대로 있는 선택지 문구를 되살린다. 조건이 하나라도 있을 때만 본다.
	// 선택지 문구만 되살리고 문항 문구·일반어("수", "명" 등 GENERIC)는 되살리지 않는다.
	// 원문에서 이미 든 조건이나 쓰이는 target·topic·sort.by가 나온 자리 안의 글자도 그대로 둔다("기획팀" 안의 "기획").
	// 낱말 경계(앞은 글자가 아님, 뒤는 끝·공백·문장부호·조사)에서만 찾아 "예정" 속 "예" 같은 글자를 되살리지 않는다.
	static RequestContext reviveDroppedOptions(String query, RequestContext parsed, List<VersionQuestions> versions) {
		if (parsed.conditions().isEmpty()) {
			return parsed;
		}
		List<String> revived = droppedOptions(query, parsed, versions);
		return revived.isEmpty() ? parsed : parsed.withAddedConditions(revived);
	}

	// 원문에 낱말 경계로 나오는데 이미 든 조건·쓰이는 키·문항 문구 자리 밖에 있는 선택지 문구. 원문 순서대로.
	private static List<String> droppedOptions(String query, RequestContext parsed, List<VersionQuestions> versions) {
		// "문항 - 값" 조건은 원문에 그 글자로 나오지 않으므로 문항 부분과 값 부분이 나온 자리도 쓰인 자리다.
		List<int[]> used = new ArrayList<>();
		Stream.concat(parsed.conditions().stream(), usedPhrases(parsed))
			.flatMap(phrase -> Stream.concat(Stream.of(phrase), LiteralMatcher.split(phrase)
				.stream()
				.flatMap(split -> Stream.of(split.question(), split.value()))))
			.filter(phrase -> !phrase.isBlank())
			.forEach(phrase -> used.addAll(spans(query, phrase, false)));
		List<int[]> wording = new ArrayList<>();
		versions.stream()
			.flatMap(version -> version.questions().stream())
			.map(VersionQuestions.Question::questionNote)
			.distinct()
			.forEach(note -> spans(query, note, false).forEach(wording::add));

		Map<Integer, String> found = new TreeMap<>();
		Map<Integer, Integer> ends = new TreeMap<>();
		versions.stream()
			.flatMap(version -> version.questions().stream())
			.filter(VersionQuestions.Question::isChoice)
			.flatMap(question -> question.options().stream())
			.filter(option -> !option.isBlank() && !isGeneric(option))
			.distinct()
			.forEach(option -> spans(query, option, true).forEach(span -> {
				// 같은 자리에서 시작하면 긴 선택지를 남긴다.
				if (!found.containsKey(span[0]) || found.get(span[0]).length() < option.length()) {
					found.put(span[0], option);
					ends.put(span[0], span[1]);
				}
			}));
		List<String> revived = new ArrayList<>();
		for (Map.Entry<Integer, String> entry : found.entrySet()) {
			int start = entry.getKey();
			int end = ends.get(start);
			boolean insideOther = found.keySet()
				.stream()
				.anyMatch(other -> other != start && other <= start && ends.get(other) >= end);
			boolean insideWording = wording.stream()
				.anyMatch(span -> span[0] <= start && span[1] >= end && span[1] - span[0] > end - start);
			boolean insideUsed = used.stream().anyMatch(span -> span[0] <= start && span[1] >= end);
			if (!insideOther && !insideWording && !insideUsed && !revived.contains(entry.getValue())) {
				revived.add(entry.getValue());
			}
		}
		return revived;
	}

	private static Stream<String> usedPhrases(RequestContext context) {
		boolean summarize = RequestContext.SUMMARIZE.equals(context.operation());
		boolean list = RequestContext.LIST.equals(context.operation());
		String sortBy = list && context.sort() != null ? context.sort().by() : null;
		return Stream.of(summarize ? context.target() : null, summarize ? context.topic() : null, sortBy)
			.filter(Objects::nonNull);
	}

	// text 안에서 phrase가 나오는 [시작, 끝). bounded면 낱말 경계에 있는 것만 센다.
	private static List<int[]> spans(String text, String phrase, boolean bounded) {
		List<int[]> spans = new ArrayList<>();
		for (int start = text.indexOf(phrase); start >= 0; start = text.indexOf(phrase, start + 1)) {
			int end = start + phrase.length();
			if (!bounded || startsWord(text, start) && endsWord(text, end)) {
				spans.add(new int[] { start, end });
			}
		}
		return spans;
	}

	private static boolean startsWord(String text, int start) {
		return start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
	}

	// 토큰은 공백이나 문장부호·기호에서 끊는다("소프트웨어학과,3학년").
	private static boolean endsWord(String text, int end) {
		int tokenEnd = end;
		while (tokenEnd < text.length() && !Character.isWhitespace(text.charAt(tokenEnd))
				&& !WORD_BREAK.matcher(String.valueOf(text.charAt(tokenEnd))).matches()) {
			tokenEnd++;
		}
		String rest = EDGE_PUNCTUATION.matcher(text.substring(end, tokenEnd)).replaceAll("");
		return rest.isEmpty() || OPTION_PARTICLES.contains(rest);
	}

	private static List<String> wordsWithoutAction(String query) {
		return tokens(query).stream()
			.map(token -> ACTION_TAIL.matcher(token).replaceFirst(""))
			.map(QueryText::withoutExistTail)
			.filter(word -> !word.isEmpty())
			.toList();
	}

	// 낱말 자체가 "있어"·"있나요"면(선택지일 수 있다) 떼지 않는다.
	private static String withoutExistTail(String token) {
		String stripped = EXIST_TAIL.matcher(token).replaceFirst("");
		return stripped.isEmpty() ? token : stripped;
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

	// 조사를 뗀 낱말이 응답이 남은 버전의 문항 문구 안에 있고 선택지는 아니다.
	private static boolean isQuestionWording(String token, List<VersionQuestions> versions) {
		String stem = TRAILING_PARTICLE.matcher(token).replaceFirst("");
		if (stem.isEmpty()) {
			return false;
		}
		List<VersionQuestions.Question> questions = versions.stream().flatMap(version -> version.questions().stream()).toList();
		return questions.stream().noneMatch(question -> question.options().contains(stem))
				&& questions.stream().anyMatch(question -> question.questionNote().contains(stem));
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
