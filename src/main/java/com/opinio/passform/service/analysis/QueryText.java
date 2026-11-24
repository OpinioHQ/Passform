package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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

	// 되살리기에서 조건으로 보지 않는 빈도·질문 말과 누적 말(사람이 정한 목록). 범위를 좁히는 말(오늘, 최근 등)은 넣지 않는다.
	// 이 폼의 선택지 문구와 같으면 조건으로 되살린다.
	private static final Set<String> FILLERS = Set.of("가장", "제일", "많이", "자주", "나온", "뭐야", "뭐지", "어때", "지금까지", "현재까지",
			"들어왔어", "받았어", "모였어");

	// #139 정렬 표현의 모양. sort가 있을 때만 쓴다. 조사는 낱말 끝에 붙을 때만 허용한다.
	private static final String SORT_PARTICLES = "(으로|로|대로|이|가|은|는|을|를|의|에)?";

	private static final String SORT_SUFFIX = "(순|순서|기준|오름차순|내림차순)";

	// 그 자체로 정렬 말인 낱말("순으로", "순서대로", "기준", "내림차순으로").
	private static final Pattern SORT_WORD = Pattern.compile("^" + SORT_SUFFIX + SORT_PARTICLES + "$");

	// 정렬 기준 낱말 뒤에 붙을 수 있는 꼬리("학년순으로"의 "순으로", "나이가"의 "가").
	private static final Pattern SORT_TAIL = Pattern.compile("^" + SORT_SUFFIX + "?" + SORT_PARTICLES + "$");

	// 방향 말(파서 프롬프트의 방향 표현). "낮은순"처럼 붙었거나, 바로 다음 낱말이 "순"·"순서"일 때만 정렬 말이다.
	private static final Pattern DIRECTION = Pattern.compile("^(적은|어린|낮은|많은|높은)((순|순서)" + SORT_PARTICLES + ")?$");

	private static final Pattern DIRECTION_STEP = Pattern.compile("^(순|순서)" + SORT_PARTICLES + "$");

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
		boolean[] sortPhrase = sortPhraseWords(words, context);
		for (int index = 0; index < words.size(); index++) {
			String word = words.get(index);
			if (isNegation(word)) {
				negated = true;
				continue;
			}
			boolean beforeBareNegation = index + 1 < words.size() && NEGATIONS.contains(words.get(index + 1));
			if (!beforeBareNegation && !sortPhrase[index] && isConditionWord(word, context)) {
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
	// 요약에서만 "있어" 끝말을 뗀다(summaryWords). count·list·동작 없음은 낱말 그대로 되살린다("의견있어 몇 명?").
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
		RequestContext keys = sortAsPhrase(query, parsed, versions);
		boolean summarize = RequestContext.SUMMARIZE.equals(parsed.operation());
		List<String> allWords = summarize ? summaryWords(query, versions) : actionlessTokens(query).toList();
		List<String> words = withoutFillers(query, conditionWords(allWords, keys), versions);
		if (words.isEmpty()) {
			return parsed;
		}
		boolean negated = allWords.stream().anyMatch(QueryText::isNegation);
		if (!negated && summarize && summaryPhrases(keys).anyMatch(phrase -> !phrase.isBlank())) {
			List<String> options = droppedOptions(query, keys, versions);
			if (!options.isEmpty() && conditionWordsOutside(query, options, keys, versions).isEmpty()) {
				return parsed.withRevivedConditions(options);
			}
		}
		return parsed.withRevivedConditions(negated ? List.of(String.join(" ", words)) : words);
	}

	// #139: 되살리기에서 sort를 정렬 표현으로 볼 파서 출력. list의 sort는 항상 정렬 표현이다(sort.by는 뒤에서 검증한다).
	// list가 아닌 sort는 아래를 모두 만족할 때만 정렬 표현으로 본다(SPEC 318행). 하나라도 어긋나면 파서가 조건을 쓰이지
	// 않는 키에 잘못 넣었을 수 있으므로 sort를 없는 것으로 보고 되살린다(#44).
	// - sort.by가 응답이 남은 버전의 문항 문구와 글자 그대로 같다(ListSort와 같은 기준, 앞뒤 공백은 뗀다)
	// - sort.by가 어느 버전의 선택지와도 같지 않다(같은 글자의 선택지 조건을 가리지 않게)
	// - 원문에 정렬 표시가 있다("학년순으로", "학년 낮은 순", "학년 기준"). 문항 이름만 있으면("학년 몇 명") 조건일 수 있다
	private static RequestContext sortAsPhrase(String query, RequestContext parsed, List<VersionQuestions> versions) {
		if (parsed.sort() == null || RequestContext.LIST.equals(parsed.operation())) {
			return parsed;
		}
		String by = parsed.sort().by().strip();
		List<VersionQuestions.Question> questions = versions.stream().flatMap(version -> version.questions().stream()).toList();
		boolean questionWording = questions.stream().anyMatch(question -> question.questionNote().strip().equals(by));
		boolean option = questions.stream()
			.flatMap(question -> question.options().stream())
			.anyMatch(choice -> choice.strip().equals(by));
		boolean marked = hasSortMark(actionlessTokens(query).toList(), sortByWords(by));
		return questionWording && !option && marked ? parsed
				: new RequestContext(parsed.conditions(), parsed.match(), parsed.operation(), null, parsed.target(),
						parsed.topic());
	}

	// sort.by를 이루는 낱말과 띄어쓰기를 뺀 전체("소속 학과" → 소속, 학과, 소속학과).
	private static List<String> sortByWords(String by) {
		List<String> words = new ArrayList<>(tokens(by));
		String compact = WHITESPACE.matcher(by).replaceAll("");
		if (!compact.isEmpty() && !words.contains(compact)) {
			words.add(compact);
		}
		return words;
	}

	// 낱말이 정렬 기준 낱말로 시작하고 그 뒤에 정렬 말·조사만 붙은 꼴이면 1(꼬리 없음 또는 조사만), 정렬 말이 붙었으면 2,
	// 아니면 0. 앞에 다른 글자가 붙으면("3학년") 정렬 기준이 아니다.
	private static int sortByForm(String word, List<String> byWords) {
		int form = 0;
		for (String by : byWords) {
			if (!by.isEmpty() && word.startsWith(by)) {
				Matcher tail = SORT_TAIL.matcher(word.substring(by.length()));
				if (tail.matches()) {
					form = Math.max(form, tail.group(1) != null ? 2 : 1);
				}
			}
		}
		return form;
	}

	// 원문에 정렬 기준이 정렬 표현으로 쓰였는가: "학년순으로", 또는 "학년" 바로 뒤에 정렬 말("학년 순으로", "학년 낮은 순").
	private static boolean hasSortMark(List<String> words, List<String> byWords) {
		for (int index = 0; index < words.size(); index++) {
			int form = sortByForm(words.get(index), byWords);
			if (form == 2 || form == 1 && sortWordAt(words, index + 1)) {
				return true;
			}
		}
		return false;
	}

	// index 자리의 낱말이 정렬 말인가. 띄어 쓴 방향 말은 다음 낱말이 "순"·"순서"일 때만 정렬 말이다("낮은 순"은 맞고
	// "높은 순위"는 아니다).
	private static boolean sortWordAt(List<String> words, int index) {
		if (index >= words.size()) {
			return false;
		}
		String word = words.get(index);
		if (SORT_WORD.matcher(word).matches()) {
			return true;
		}
		Matcher direction = DIRECTION.matcher(word);
		if (!direction.matches()) {
			return false;
		}
		return direction.group(2) != null
				|| index + 1 < words.size() && DIRECTION_STEP.matcher(words.get(index + 1)).matches();
	}

	// 낱말마다 정렬 표현인지. sort가 없으면 모두 아니다. 정렬 기준 낱말(꼴이 맞을 때만)과 정렬 말만 정렬 표현이다.
	private static boolean[] sortPhraseWords(List<String> words, RequestContext context) {
		boolean[] sortPhrase = new boolean[words.size()];
		if (context.sort() == null) {
			return sortPhrase;
		}
		List<String> byWords = sortByWords(context.sort().by().strip());
		for (int index = 0; index < words.size(); index++) {
			sortPhrase[index] = sortByForm(words.get(index), byWords) > 0 || sortWordAt(words, index);
		}
		return sortPhrase;
	}

	// 조건으로 되살릴 낱말: 일반어도, 정렬 표현도, target·topic에 담긴 표현도 아닌 낱말.
	private static List<String> conditionWords(List<String> words, RequestContext context) {
		boolean[] sortPhrase = sortPhraseWords(words, context);
		List<String> conditions = new ArrayList<>();
		for (int index = 0; index < words.size(); index++) {
			if (!sortPhrase[index] && isConditionWord(words.get(index), context)) {
				conditions.add(words.get(index));
			}
		}
		return conditions;
	}

	// 선택지 문구가 낱말 경계로 나온 자리를 지운 뒤에도 남는 조건 낱말. 선택지 바로 뒤에 붙었던 조사("3학년인"의 "인")는 뺀다.
	private static List<String> conditionWordsOutside(String query, List<String> options, RequestContext parsed,
			List<VersionQuestions> versions) {
		StringBuilder rest = new StringBuilder(query);
		for (String option : options) {
			for (int[] span : spans(query, option, true)) {
				for (int index = span[0]; index < span[1]; index++) {
					rest.setCharAt(index, ' ');
				}
			}
		}
		List<String> words = summaryWords(rest.toString(), versions);
		boolean[] sortPhrase = sortPhraseWords(words, parsed);
		List<String> conditions = new ArrayList<>();
		for (int index = 0; index < words.size(); index++) {
			String word = words.get(index);
			if (!sortPhrase[index] && !OPTION_PARTICLES.contains(word) && isConditionWord(word, parsed)) {
				conditions.add(word);
			}
		}
		return withoutFillers(query, conditions, versions);
	}

	// 빈도·질문 말과 누적 말을 뺀다. 이 폼의 선택지 문구와 같은 낱말과, 원문에 낱말 경계로 나온 여러 낱말 선택지
	// ("가장 많이")를 이루는 낱말은 남긴다.
	private static List<String> withoutFillers(String query, List<String> words, List<VersionQuestions> versions) {
		Set<String> kept = new HashSet<>(choiceOptions(versions));
		choiceOptions(versions).stream()
			.filter(option -> tokens(option).size() > 1 && !spans(query, option, true).isEmpty())
			.forEach(option -> kept.addAll(tokens(option)));
		return words.stream().filter(word -> !FILLERS.contains(word) || kept.contains(word)).toList();
	}

	// 파서가 summarize의 target을 비우고 문항 이름을 topic에 넣었으면 target으로 옮긴다({query}에만, 되살리기보다 먼저. SPEC 319행).
	// topic이 어느 버전의 문항 문구 그 자체일 때만 옮긴다(띄어쓰기·문장부호는 무시하고, 뒤에 조사만 붙은 꼴은 같게 본다).
	// 문항 문구에 다른 말이 더 붙은 topic("좋았던 점 중 강연이 좋았다")은 의견 찾기이므로 그대로 둔다.
	static RequestContext topicToTarget(RequestContext parsed, List<VersionQuestions> versions) {
		boolean noTarget = parsed.target() == null || parsed.target().isBlank();
		if (!RequestContext.SUMMARIZE.equals(parsed.operation()) || !noTarget || parsed.topic() == null) {
			return parsed;
		}
		String topic = compact(parsed.topic());
		List<String> notes = versions.stream()
			.flatMap(version -> version.questions().stream())
			.map(VersionQuestions.Question::questionNote)
			.filter(note -> !note.isBlank())
			.distinct()
			.toList();
		for (String note : notes) {
			String compactNote = compact(note);
			if (!compactNote.isEmpty() && topic.startsWith(compactNote)
					&& (topic.length() == compactNote.length() || OPTION_PARTICLES.contains(topic.substring(compactNote.length())))) {
				return new RequestContext(parsed.conditions(), parsed.match(), parsed.operation(), parsed.sort(), note, null);
			}
		}
		return parsed;
	}

	private static String compact(String text) {
		return EDGE_PUNCTUATION.matcher(WHITESPACE.matcher(text).replaceAll("")).replaceAll("");
	}

	static Set<String> choiceOptionsOf(List<VersionQuestions> versions) {
		return choiceOptions(versions);
	}

	// 원문에서 phrases(문항 문구·선택지)가 낱말 경계로 나온 자리를 지운 뒤 남는 낱말 가운데 조사·일반어·빈도 말·동작어가
	// 아닌 것. 비어 있으면 원문에 그 문구들 말고 뜻 있는 말이 없다는 뜻이다(파서가 원문의 말을 빼먹었는지 원문으로 확인한다).
	static List<String> residualWords(String query, List<String> phrases, List<VersionQuestions> versions) {
		StringBuilder rest = new StringBuilder(query);
		for (String phrase : phrases) {
			for (int[] span : spans(query, phrase, true)) {
				blank(rest, span);
			}
		}
		Set<String> options = choiceOptions(versions);
		// 일반어·빈도 말이어도 이 폼의 선택지와 같으면("학생") 파서가 빼먹은 조건일 수 있으므로 남은 말로 센다.
		return summaryWords(rest.toString(), versions).stream()
			.filter(word -> !OPTION_PARTICLES.contains(word))
			.filter(word -> !(isGeneric(word) || FILLERS.contains(word)) || isOptionWord(word, options))
			.toList();
	}

	static boolean isGenericWord(String token) {
		return isGeneric(token);
	}

	static boolean hasNegation(String query) {
		return tokens(query).stream().anyMatch(QueryText::isNegation);
	}

	private static Set<String> choiceOptions(List<VersionQuestions> versions) {
		return versions.stream()
			.flatMap(version -> version.questions().stream())
			.filter(VersionQuestions.Question::isChoice)
			.flatMap(question -> question.options().stream())
			.collect(Collectors.toSet());
	}

	// 원문에서 찾은 문구와 그 자리 [시작, 끝).
	record Found(String phrase, int[] span) {
	}

	// 더 긴 문구가 찾은 자리 안에 든 짧은 문구는 뺀다("보호자 이름" 안의 "이름"). 자리 순서대로다.
	static List<Found> longestOnly(List<Found> found) {
		return found.stream()
			.filter(entry -> found.stream()
				.noneMatch(other -> other.phrase().length() > entry.phrase().length() && other.span()[0] <= entry.span()[0]
						&& other.span()[1] >= entry.span()[1]))
			.sorted(java.util.Comparator.comparingInt(entry -> entry.span()[0]))
			.toList();
	}

	static List<int[]> boundedSpans(String text, String phrase) {
		return spans(text, phrase, true);
	}

	static void blank(StringBuilder text, int[] span) {
		for (int index = span[0]; index < span[1]; index++) {
			text.setCharAt(index, ' ');
		}
	}

	// target의 나머지에 조사와 일반어만 있는가. 일반어여도 이 폼의 선택지와 같으면("학생들의" ↔ 선택지 "학생") 조건이므로
	// 버려지는 말로 보지 않는다.
	static boolean onlyParticlesAndGeneric(String rest, List<VersionQuestions> versions) {
		Set<String> options = choiceOptions(versions);
		return tokens(rest).stream()
			.allMatch(word -> OPTION_PARTICLES.contains(word) || isGeneric(word) && !isOptionWord(word, options));
	}

	// 조사를 뗀 꼴까지 선택지와 같은 낱말인가.
	private static boolean isOptionWord(String token, Set<String> options) {
		String stem = token;
		for (int count = 0; count < 3; count++) {
			if (options.contains(stem)) {
				return true;
			}
			stem = TRAILING_PARTICLE.matcher(stem).replaceFirst("");
		}
		return false;
	}

	// SPEC 334행: 파서가 조건 일부를 버렸으면 원문에 글자 그대로 있는 선택지 문구를 되살린다. 조건이 하나라도 있을 때만 본다.
	// 선택지 문구만 되살리고 문항 문구·일반어("수", "명" 등 GENERIC)는 되살리지 않는다.
	// 원문에서 이미 든 조건이나 쓰이는 target·topic·sort.by가 나온 자리 안의 글자도 그대로 둔다("기획팀" 안의 "기획").
	// 낱말 경계(앞은 글자가 아님, 뒤는 끝·공백·문장부호·조사)에서만 찾아 "예정" 속 "예" 같은 글자를 되살리지 않는다.
	static RequestContext reviveDroppedOptions(String query, RequestContext parsed, List<VersionQuestions> versions) {
		if (parsed.conditions().isEmpty()) {
			return parsed;
		}
		List<String> revived = droppedOptions(query, sortAsPhrase(query, parsed, versions), versions);
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

	// 원문에서 버린 조건으로 보지 않는 파서 키의 표현. sort는 sortAsPhrase를 거친 출력에 남아 있을 때 넣는다(#139).
	private static Stream<String> usedPhrases(RequestContext context) {
		String sortBy = context.sort() != null ? context.sort().by() : null;
		return Stream.concat(summaryPhrases(context), Stream.ofNullable(sortBy));
	}

	// 요약에서 쓰이는 target·topic 표현.
	private static Stream<String> summaryPhrases(RequestContext context) {
		boolean summarize = RequestContext.SUMMARIZE.equals(context.operation());
		return Stream.of(summarize ? context.target() : null, summarize ? context.topic() : null).filter(Objects::nonNull);
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
		return actionlessTokens(query).map(QueryText::withoutExistTail).toList();
	}

	private static Stream<String> actionlessTokens(String query) {
		return tokens(query).stream()
			.map(token -> ACTION_TAIL.matcher(token).replaceFirst(""))
			.filter(word -> !word.isEmpty());
	}

	// 되살리기에서 "있어" 끝말은 요약에서만 뗀다("의견있어" → "의견"). count·list·동작 없음은 떼지 않는다(팀원 리뷰 반영).
	// 낱말 전체가 "있어"류면("의견 있어?") 뺀다. 이 폼의 선택지 문구와 같으면 조건이므로 남긴다.
	private static List<String> summaryWords(String query, List<VersionQuestions> versions) {
		Set<String> options = versions.stream()
			.flatMap(version -> version.questions().stream())
			.filter(VersionQuestions.Question::isChoice)
			.flatMap(question -> question.options().stream())
			.collect(Collectors.toSet());
		return actionlessTokens(query)
			.filter(token -> !EXIST_TAIL.matcher(token).replaceFirst("").isEmpty() || options.contains(token))
			.map(QueryText::withoutExistTail)
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

	// target·topic에 담긴 표현은 버린 조건이 아니다. 요약에서만 쓰이는 키라 요약일 때만 본다. 의견 표현은 꼴이 다양해
	// ("부담된다는" ↔ "부담된다") 포함으로 비교한다. sort는 포함으로 보면 "3학년"이 "학년"에 덮이므로(#139)
	// 낱말 모양으로 따로 본다(sortPhraseWords).
	private static boolean coveredByParsedKeys(String token, RequestContext context) {
		return summaryPhrases(context).flatMap(phrase -> tokens(phrase).stream())
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
