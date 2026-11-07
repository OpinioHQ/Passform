package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.PendingConditionMatcher;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.AnalyzeView.QuestionOption;
import com.opinio.passform.support.TestData;

// 골든에 없는 SPEC 규칙만 시험한다. 저장소와 파서는 모의 객체이고 매처는 운영 bean이다.
class AnalysisServiceTest {

	private static final UUID OWNER = UUID.randomUUID();

	private static final UUID FORM_ID = UUID.randomUUID();

	private final QueryParser parser = mock(QueryParser.class);

	private AnalysisService service(boolean aiSummary) {
		return service(aiSummary, new PendingConditionMatcher());
	}

	private AnalysisService service(boolean aiSummary, ConditionMatcher matcher) {
		return service(aiSummary, matcher, List.of(List.of(question("q1", "학과", "dropdown", "소프트웨어학과", "경영학과"),
				question("q2", "지원 동기", "long_answer"))));
	}

	// 버전마다 문항 목록 하나와 제출 응답 하나를 둔다. 폼의 최신 버전은 마지막 버전이다.
	private AnalysisService service(boolean aiSummary, ConditionMatcher matcher,
			List<List<Map<String, Object>>> versionQuestions) {
		FormVersionRepository versions = mock(FormVersionRepository.class);
		List<Response> submitted = new java.util.ArrayList<>();
		Map<String, Object> definition = Map.of();
		for (int index = 0; index < versionQuestions.size(); index++) {
			int version = index + 1;
			definition = Map.of("settings", Map.of("ai_summary", Map.of("enabled", aiSummary)), "sections",
					List.of(Map.of("section_id", "s1", "questions", versionQuestions.get(index))));
			when(versions.findById(new FormVersionId(FORM_ID, version)))
				.thenReturn(Optional.of(new FormVersion(FORM_ID, version, definition, Instant.EPOCH)));
			Response response = TestData.submitted(FORM_ID, OWNER, version, TestData.NOW);
			ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
			submitted.add(response);
		}
		Form form = new Form(OWNER, definition, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", FORM_ID);
		ReflectionTestUtils.setField(form, "definitionVersion", versionQuestions.size());
		FormRepository forms = mock(FormRepository.class);
		when(forms.findById(FORM_ID)).thenReturn(Optional.of(form));
		ResponseRepository responses = mock(ResponseRepository.class);
		when(responses.findSubmittedNotDeletedByFormId(FORM_ID)).thenReturn(submitted);
		return new AnalysisService(forms, versions, responses, parser, matcher, new RequestContextSchema());
	}

	private static Map<String, Object> question(String id, String note, String type, String... options) {
		return Map.of("question_id", id, "question_note", note, "question_type", type, "options", List.of(options));
	}

	private static ConditionMatch option(String condition, int version, String questionId, String option) {
		return new ConditionMatch(condition, version, questionId, MatchKind.OPTION, option);
	}

	private ConditionMatcher matcherReturning(ConditionMatch... matches) {
		ConditionMatcher matcher = mock(ConditionMatcher.class);
		when(matcher.match(anyList(), anyList())).thenReturn(List.of(matches));
		return matcher;
	}

	private static String queryBody(String query) {
		return "{\"query\":\"" + query + "\"}";
	}

	private void parserReturns(String first, String... rest) {
		when(parser.parse(anyString(), nullable(String.class))).thenReturn(first, rest);
	}

	private static AnalyzeCommand command(String body) {
		return new AnalyzeCommand(OWNER, FORM_ID.toString(), body);
	}

	private AnalyzeView askQuery(boolean aiSummary, String query) {
		return service(aiSummary).analyze(command("{\"query\":\"" + query + "\"}"));
	}

	private AnalyzeView askContext(boolean aiSummary, String context) {
		return service(aiSummary).analyze(command("{\"context\":" + context + "}"));
	}

	@Test
	void AC30_한_번_실패_후_정상이면_파서_2회이고_오류를_붙여_다시_부른다() {
		parserReturns("JSON이 아님", "{\"conditions\":[],\"operation\":\"count\"}");

		AnalyzeView view = askQuery(false, "전체 응답 수 알려줘");

		assertThat(view.status()).isEqualTo("ok");
		verify(parser, times(1)).parse(anyString(), isNull());
		verify(parser, times(1)).parse(anyString(), notNull());
	}

	@Test
	void AC30_파서_호출_실패는_되묻기가_아니라_503이다() {
		when(parser.parse(anyString(), nullable(String.class))).thenThrow(new DomainException(ErrorCode.LLM_UNAVAILABLE));

		assertThatThrownBy(() -> askQuery(false, "전체 응답 수 알려줘")).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNAVAILABLE);
		verify(parser, times(1)).parse(anyString(), nullable(String.class));
	}

	@Test
	void AC32_query와_같은_값의_context는_같은_결과를_낸다() {
		String context = "{\"conditions\":[],\"match\":\"all\",\"operation\":\"list\"}";
		parserReturns(context);

		AnalyzeView fromQuery = askQuery(false, "전체 응답 보여줘");
		AnalyzeView fromContext = askContext(false, context);

		assertThat(fromContext.status()).isEqualTo("ok");
		assertThat(fromContext.context()).isEqualTo(fromQuery.context());
		assertThat(fromContext.coverage()).isEqualTo(fromQuery.coverage());
		assertThat(fromContext.result().count()).isEqualTo(fromQuery.result().count());
		assertThat(fromContext.result().operation()).isEqualTo(fromQuery.result().operation());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "{}", "{", "[]", "{\"query\":\"수\",\"context\":{\"conditions\":[],\"operation\":\"count\"}}",
			"{\"context\":{\"conditions\":[],\"match\":\"and\",\"operation\":\"count\"}}",
			"{\"context\":{\"conditions\":[],\"operation\":\"statistics\"}}", "{\"context\":{\"conditions\":[]}}",
			"{\"context\":{\"conditions\":[],\"operation\":\"count\",\"form_version\":1}}" })
	void AC32_잘못된_본문은_파서를_부르지_않고_invalid_context다(String body) {
		assertThatThrownBy(() -> service(false).analyze(command(body))).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_CONTEXT);
		verify(parser, never()).parse(anyString(), nullable(String.class));
	}

	@Test
	void AC77_버전별_요청은_파서를_부르지_않고_unsupported_scope다() {
		AnalyzeView view = askQuery(false, "버전별 응답 수 알려줘");

		assertThat(view.clarify().reason()).isEqualTo("unsupported_scope");
		assertThat(view.context()).isNull();
		verify(parser, never()).parse(anyString(), nullable(String.class));
	}

	@Test
	void AC77_평균_요청은_파서가_count를_내도_missing_operation이다() {
		parserReturns("{\"conditions\":[],\"operation\":\"count\"}");

		AnalyzeView view = askQuery(false, "평균 나이 알려줘");

		assertThat(view.clarify().reason()).isEqualTo("missing_operation");
		assertThat(view.result()).isNull();
	}

	@Test
	void AC27_AI_요약이_꺼진_폼의_되묻기에는_summarize가_없다() {
		AnalyzeView view = askContext(false, "{\"conditions\":[],\"operation\":null}");

		assertThat(view.clarify().reason()).isEqualTo("missing_operation");
		assertThat(view.clarify().options()).isEqualTo(List.of("count", "list"));
	}

	@Test
	void AC27_AI_요약이_꺼진_폼의_summarize는_summary_disabled다() {
		AnalyzeView view = askContext(false, "{\"conditions\":[],\"operation\":\"summarize\"}");

		assertThat(view.clarify().reason()).isEqualTo("summary_disabled");
		assertThat(view.clarify().options()).isEqualTo(List.of("count", "list"));
	}

	@Test
	void AC26_혼합_부정은_선택지_없이_missing_match다() {
		parserReturns("{\"conditions\":[\"소프트웨어학과\",\"4학년\"],\"operation\":\"count\"}");

		AnalyzeView view = askQuery(false, "소프트웨어학과가 아닌 4학년 수");

		assertThat(view.clarify().reason()).isEqualTo("missing_match");
		assertThat(view.clarify().options()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"conditions\":[],\"operation\":\"count\",\"target\":\"수의학과\"}",
			"{\"conditions\":[],\"operation\":\"count\",\"topic\":\"수의학과\"}",
			"{\"conditions\":[],\"operation\":\"count\",\"sort\":{\"by\":\"수의학과\",\"order\":\"asc\"}}" })
	void 동작에_쓰이지_않는_키의_표현은_조건으로_되살려_전체_건수를_내지_않는다(String parserOutput) {
		parserReturns(parserOutput);

		AnalyzeView view = service(false, matcherReturning()).analyze(command(queryBody("수의학과 수 알려줘")));

		assertThat(view.clarify().reason()).isEqualTo("unknown_condition");
		assertThat(view.context().conditions()).containsExactly("수의학과");
		assertThat(view.result()).isNull();
	}

	@Test
	void 요약에_쓰이는_target의_표현은_되살리지_않고_target을_유지한다() {
		RequestContext parsed = new RequestContext(List.of(), null, "summarize", null, "지원 동기", null);

		RequestContext repaired = QueryText.reviveDroppedConditions("지원 동기 요약해줘", parsed).withoutUnusedKeys();

		assertThat(repaired.conditions()).isEmpty();
		assertThat(repaired.target()).isEqualTo("지원 동기");
	}

	@Test
	void 동작어가_붙은_낱말의_앞부분은_조건으로_되살린다() {
		parserReturns("{\"conditions\":[],\"operation\":\"count\"}");

		AnalyzeView view = service(false, matcherReturning()).analyze(command(queryBody("수의학과만세어줘")));

		assertThat(view.clarify().reason()).isEqualTo("unknown_condition");
		assertThat(view.result()).isNull();
	}

	@Test
	void 여러_낱말로_된_단일_부정은_혼합_부정이_아니다() {
		parserReturns("{\"conditions\":[\"소프트웨어 학과\"],\"match\":\"not\",\"operation\":\"count\"}");
		ConditionMatcher matcher = matcherReturning(
				new ConditionMatch("소프트웨어 학과", 1, "q1", MatchKind.OPTION, "소프트웨어학과"));

		AnalyzeView view = service(false, matcher).analyze(command(queryBody("소프트웨어 학과가 아닌 학생 수")));

		assertThat(view.status()).isEqualTo("ok");
		assertThat(view.result().count()).isEqualTo(1);
	}

	@Test
	void 파서가_버린_낱말이_조건_밖에_남으면_여전히_혼합_부정이다() {
		parserReturns("{\"conditions\":[\"소프트웨어학과\"],\"match\":\"not\",\"operation\":\"count\"}");

		AnalyzeView view = askQuery(false, "소프트웨어학과가 아닌 4학년 수");

		assertThat(view.clarify().reason()).isEqualTo("missing_match");
		assertThat(view.clarify().options()).isEmpty();
	}

	@Test
	void 문구와_유형이_같고_ID만_다른_두_버전은_같은_문항으로_보고_되묻지_않는다() {
		ConditionMatcher matcher = matcherReturning(option("경영학과", 1, "q1", "경영학과"), option("경영학과", 2, "q9", "경영학과"));
		AnalysisService service = service(false, matcher,
				List.of(List.of(question("q1", "학과", "dropdown", "소프트웨어학과", "경영학과")),
						List.of(question("q9", "학과", "dropdown", "소프트웨어학과", "경영학과"))));

		AnalyzeView view = service.analyze(command("{\"context\":{\"conditions\":[\"경영학과\"],\"operation\":\"count\"}}"));

		assertThat(view.status()).isEqualTo("ok");
		assertThat(view.matched()).extracting(AnalyzeView.MatchedView::formVersion).containsExactly(1, 2);
	}

	@Test
	void 문구가_같아도_유형이_다르면_ambiguous_match다() {
		ConditionMatcher matcher = matcherReturning(option("경영학과", 1, "q1", "경영학과"), option("경영학과", 2, "q9", "경영학과"));
		AnalysisService service = service(false, matcher,
				List.of(List.of(question("q1", "학과", "dropdown", "소프트웨어학과", "경영학과")),
						List.of(question("q9", "학과", "checkbox", "소프트웨어학과", "경영학과"))));

		AnalyzeView view = service.analyze(command("{\"context\":{\"conditions\":[\"경영학과\"],\"operation\":\"count\"}}"));

		assertThat(view.clarify().reason()).isEqualTo("ambiguous_match");
	}

	@Test
	void 같은_버전의_같은_문구_두_문항은_ambiguous_match다() {
		ConditionMatcher matcher = matcherReturning(option("경영학과", 1, "q1", "경영학과"), option("경영학과", 1, "q2", "경영학과"));
		AnalysisService service = service(false, matcher,
				List.of(List.of(question("q1", "학과", "dropdown", "경영학과"), question("q2", "학과", "dropdown", "경영학과"))));

		AnalyzeView view = service.analyze(command("{\"context\":{\"conditions\":[\"경영학과\"],\"operation\":\"count\"}}"));

		assertThat(view.clarify().reason()).isEqualTo("ambiguous_match");
	}

	@Test
	void 문구와_유형이_같고_ID만_다른_target은_되묻지_않는다() {
		AnalysisService service = service(true, matcherReturning(),
				List.of(List.of(question("q2", "지원 동기", "long_answer")), List.of(question("q8", "지원 동기", "long_answer"))));
		String body = "{\"context\":{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기\"}}";

		assertThatThrownBy(() -> service.analyze(command(body))).isInstanceOf(AnalysisStepPendingException.class);
	}

	private AnalysisService gradeForm(ConditionMatcher matcher) {
		return service(false, matcher, List.of(List.of(question("q1", "성적", "multiple_choice", "평균 이상", "보통", "평균 이하"))));
	}

	@Test
	void 선택지_안의_통계_낱말은_통계_요청으로_세지_않는다() {
		parserReturns("{\"conditions\":[\"평균 이상\"],\"operation\":\"count\"}");

		AnalyzeView view = gradeForm(matcherReturning(option("평균 이상", 1, "q1", "평균 이상")))
			.analyze(command(queryBody("평균 이상 고른 사람 몇 명")));

		assertThat(view.status()).isEqualTo("ok");
	}

	@Test
	void 선택지_밖의_통계_표현이_남으면_missing_operation이다() {
		parserReturns("{\"conditions\":[\"평균 이상\"],\"operation\":\"count\"}");

		AnalyzeView view = gradeForm(matcherReturning()).analyze(command(queryBody("평균 이상 비율 알려줘")));

		assertThat(view.clarify().reason()).isEqualTo("missing_operation");
	}

	// 되살린 낱말이 둘이면 match를 지우므로 표 5행이 6행보다 앞선다(사람 결정).
	@Test
	void 선택지의_통계_낱말을_뺀_요청도_전체_건수를_내지_않는다() {
		parserReturns("{\"conditions\":[],\"operation\":\"count\"}");

		AnalyzeView view = gradeForm(matcherReturning()).analyze(command(queryBody("평균 점수 알려줘")));

		assertThat(view.result()).isNull();
		assertThat(view.clarify().reason()).isEqualTo("missing_match");
	}

	@Test
	void AC92_target이_선택형_문항이면_요약_가능_문항만_안내한다() {
		AnalyzeView view = askContext(true, "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"학과\"}");

		assertThat(view.clarify().reason()).isEqualTo("unsummarizable_field");
		assertThat(view.clarify().message()).contains("대시보드");
		assertThat(view.clarify().options()).isEqualTo(List.of(new QuestionOption(1, "q2", "지원 동기", null)));
	}

	@Test
	void 여러_사유가_겹치면_표의_위에_있는_것_하나만_나온다() {
		AnalyzeView view = askContext(false, "{\"conditions\":[\"가\",\"나\"],\"operation\":\"summarize\"}");

		assertThat(view.clarify().reason()).isEqualTo("summary_disabled");
	}

	@Test
	void 정렬_목록은_다른_순서를_내지_않고_실패한다() {
		String context = "{\"conditions\":[],\"operation\":\"list\",\"sort\":{\"by\":\"학과\",\"order\":\"asc\"}}";

		assertThatThrownBy(() -> askContext(false, context)).isInstanceOf(AnalysisStepPendingException.class)
			.hasMessageContaining("E7");
	}

	@Test
	void 되묻기를_통과한_요약은_빈_요약을_내지_않고_실패한다() {
		String context = "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기\"}";

		assertThatThrownBy(() -> askContext(true, context)).isInstanceOf(AnalysisStepPendingException.class)
			.hasMessageContaining("T7");
	}

	@Test
	void 조건이_있는데_운영_매처면_되묻기로_바꾸지_않고_503이다() {
		assertThatThrownBy(() -> askContext(false, "{\"conditions\":[\"경영학과\"],\"operation\":\"count\"}"))
			.isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNAVAILABLE);
	}

}
