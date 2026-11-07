package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
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
		Map<String, Object> definition = Map.of("settings", Map.of("ai_summary", Map.of("enabled", aiSummary)), "sections",
				List.of(Map.of("section_id", "s1", "questions",
						List.of(Map.of("question_id", "q1", "question_note", "학과", "question_type", "dropdown", "options",
								List.of("소프트웨어학과", "경영학과")),
								Map.of("question_id", "q2", "question_note", "지원 동기", "question_type", "long_answer")))));
		Form form = new Form(OWNER, definition, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", FORM_ID);
		ReflectionTestUtils.setField(form, "definitionVersion", 1);
		FormRepository forms = mock(FormRepository.class);
		when(forms.findById(FORM_ID)).thenReturn(Optional.of(form));
		FormVersionRepository versions = mock(FormVersionRepository.class);
		when(versions.findById(new FormVersionId(FORM_ID, 1)))
			.thenReturn(Optional.of(new FormVersion(FORM_ID, 1, definition, Instant.EPOCH)));
		Response response = TestData.submitted(FORM_ID, OWNER, 1, TestData.NOW);
		ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
		ResponseRepository responses = mock(ResponseRepository.class);
		when(responses.findSubmittedNotDeletedByFormId(FORM_ID)).thenReturn(List.of(response));
		return new AnalysisService(forms, versions, responses, parser, new PendingConditionMatcher(),
				new RequestContextSchema());
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
