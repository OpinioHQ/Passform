package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.analysis.LiteralMatcher;
import com.opinio.passform.domain.analysis.RagSettings;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.EmbeddingClient;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.llm.SummaryWriter;
import com.opinio.passform.llm.ThemeClassifier;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.SummaryView.ThemesItem;

import tools.jackson.databind.json.JsonMapper;

// SPEC 319·338·341행 서버 보정(EVAL_DESIGN 5절 백로그 1의 서버 안전망, 2, 3). 위임 프롬프트의 표를 옮긴 단위 시험이다.
// 파서 출력은 표에 적힌 그대로 모의 파서가 내고, 기대값은 사람이 정한 것이다. 폼은 시드·evals fixture를 읽기만 한다.
// 매처는 글자 규칙(LiteralMatcher)만 쓴다. 요약 서비스는 모의 객체이고 받은 context를 본다.
class QueryCorrectionTest {

	private static final UUID OWNER = UUID.randomUUID();

	private static final UUID FORM_ID = UUID.randomUUID();

	private static final String FORM_B = "data/seed/forms/form_b_mixed.json";

	private static final String FORM_SUMMARY = "data/seed/forms/form_summary.json";

	private static final String FORM_EVAL_SUMMARY = "evals/fixtures/form_eval_summary.json";

	private final QueryParser parser = mock(QueryParser.class);

	private final SummaryService summaries = mock(SummaryService.class);

	private static final ConditionMatcher LITERAL = (conditions, versions) -> conditions.stream()
		.distinct()
		.flatMap(condition -> versions.stream().flatMap(version -> LiteralMatcher.match(condition, version).stream()))
		.toList();

	@SuppressWarnings("unchecked")
	private AnalysisService service(String fixture) {
		Map<String, Object> seed;
		try {
			seed = JsonMapper.builder().build().readValue(Files.readString(Path.of(fixture)), Map.class);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		FormVersionRepository versions = mock(FormVersionRepository.class);
		int latest = 0;
		Map<String, Object> latestDefinition = Map.of();
		for (Map<String, Object> version : (List<Map<String, Object>>) seed.get("form_versions")) {
			int number = ((Number) version.get("form_version")).intValue();
			Map<String, Object> definition = (Map<String, Object>) version.get("form");
			when(versions.findById(new FormVersionId(FORM_ID, number)))
				.thenReturn(Optional.of(new FormVersion(FORM_ID, number, definition, Instant.EPOCH)));
			if (number > latest) {
				latest = number;
				latestDefinition = definition;
			}
		}
		List<Response> submitted = new ArrayList<>();
		for (Map<String, Object> raw : (List<Map<String, Object>>) seed.get("responses")) {
			ResponseStatus status = ResponseStatus.valueOf(((String) raw.get("status")).toUpperCase(Locale.ROOT));
			if (status != ResponseStatus.SUBMITTED) {
				continue;
			}
			List<Answer> answers = new ArrayList<>();
			for (Map<String, Object> answer : (List<Map<String, Object>>) raw.get("answers")) {
				answers.add(new Answer((String) answer.get("question_id"), answer.get("value")));
			}
			Response response = new Response(FORM_ID, null, ((Number) raw.get("form_version")).intValue(), status, answers,
					Boolean.TRUE.equals(raw.get("ai_summary_agreed")), null, Instant.parse((String) raw.get("submitted_at")));
			ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
			submitted.add(response);
		}
		Form form = new Form(OWNER, latestDefinition, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", FORM_ID);
		ReflectionTestUtils.setField(form, "definitionVersion", latest);
		FormRepository forms = mock(FormRepository.class);
		when(forms.findById(FORM_ID)).thenReturn(Optional.of(form));
		ResponseRepository responses = mock(ResponseRepository.class);
		when(responses.findSubmittedNotDeletedByFormId(FORM_ID)).thenReturn(submitted);
		when(summaries.summarize(any(), anyList(), anyList()))
			.thenReturn(new SummaryService.Outcome(new SummaryView("ok", 0, List.of(), null), List.of()));
		return new AnalysisService(forms, versions, responses, parser, LITERAL, new RequestContextSchema(), summaries);
	}

	private AnalyzeView ask(String fixture, String query, String parserOutput) {
		when(parser.parse(anyString(), nullable(String.class))).thenReturn(parserOutput);
		return service(fixture).analyze(new AnalyzeCommand(OWNER, FORM_ID.toString(), "{\"query\":\"" + query + "\"}"));
	}

	private RequestContext summarized() {
		ArgumentCaptor<RequestContext> context = ArgumentCaptor.forClass(RequestContext.class);
		verify(summaries).summarize(context.capture(), anyList(), anyList());
		return context.getValue();
	}

	private static String reason(AnalyzeView view) {
		assertThat(view.status()).isEqualTo("clarify");
		return view.clarify().reason();
	}

	// 변경 1 — 빠진 조건 되살리기

	@Test
	void 지금까지_들어왔어는_조건으로_되살리지_않고_14건이다() {
		AnalyzeView view = ask(FORM_B, "지금까지 응답 몇 개 들어왔어?", "{\"conditions\":[],\"operation\":\"count\"}");

		assertThat(view.status()).isEqualTo("ok");
		assertThat(view.context().conditions()).isEmpty();
		assertThat(view.result().count()).isEqualTo(14);
	}

	@Test
	void 현재까지_모였어는_조건으로_되살리지_않고_14건이다() {
		AnalyzeView view = ask(FORM_B, "현재까지 응답 몇 개 모였어?", "{\"conditions\":[],\"operation\":\"count\"}");

		assertThat(view.status()).isEqualTo("ok");
		assertThat(view.context().conditions()).isEmpty();
		assertThat(view.result().count()).isEqualTo(14);
	}

	@Test
	void 가장_많이_나온_뭐야는_되살리지_않고_지원_동기를_주제_요약한다() {
		AnalyzeView view = ask(FORM_SUMMARY, "지원 동기에서 가장 많이 나온 의견이 뭐야?",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.conditions()).isEmpty();
		assertThat(context.target()).isEqualTo("지원 동기");
		assertThat(context.topic()).isNull();
	}

	@Test
	void 제일_자주_나온_뭐지는_되살리지_않는다() {
		AnalyzeView view = ask(FORM_SUMMARY, "지원 동기에서 제일 자주 나온 의견 뭐지?",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기\"}");

		assertThat(view.status()).isEqualTo("ok");
		assertThat(summarized().conditions()).isEmpty();
	}

	@Test
	void 범위를_좁히는_오늘은_되살려_되묻는다() {
		AnalyzeView view = ask(FORM_B, "오늘 들어온 응답 몇 개?", "{\"conditions\":[],\"operation\":\"count\"}");

		// 되살린 낱말이 둘("오늘", "들어온")이라 결합 방식부터 되묻는다. 14건을 내지 않는다.
		assertThat(view.status()).isEqualTo("clarify");
		assertThat(view.result()).isNull();
		assertThat(view.context().conditions()).contains("오늘");
	}

	@Test
	void 남학생은_요약에서도_되살려_되묻는다() {
		AnalyzeView view = ask(FORM_SUMMARY, "남학생들의 지원 동기 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기\"}");

		assertThat(reason(view)).isEqualTo("unknown_condition");
		assertThat(view.context().conditions()).containsExactly("남학생들의");
		verify(summaries, never()).summarize(any(), anyList(), anyList());
	}

	@Test
	void 부정은_지금처럼_한_덩어리로_되살린다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "3학년 아닌 학생들의 좋았던 점 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"좋았던 점\"}");

		assertThat(view.context().conditions()).containsExactly("3학년 아닌");
		verify(summaries, never()).summarize(any(), anyList(), anyList());
	}

	@Test
	void 목록의_낱말이라도_선택지_문구와_같으면_조건으로_되살린다() {
		List<VersionQuestions> versions = List.of(VersionQuestions.of(1, Map.of("sections", List.of(Map.of("section_id",
				"s1", "questions", List.of(Map.of("question_id", "q1", "question_note", "빈도", "question_type",
						"multiple_choice", "options", List.of("자주", "가끔"))))))));
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		assertThat(QueryText.reviveDroppedConditions("자주 몇 명?", parsed, versions).conditions()).containsExactly("자주");
	}

	@Test
	void 지킴_남학생_몇_명은_지금처럼_되살려_되묻는다() {
		AnalyzeView view = ask(FORM_B, "남학생 몇 명", "{\"conditions\":[],\"operation\":\"count\"}");

		assertThat(reason(view)).isEqualTo("unknown_condition");
		assertThat(view.context().conditions()).containsExactly("남학생");
	}

	// 변경 2 — target 포함 매칭

	@Test
	void 휴대전화_번호는_휴대전화_문항이라_unsummarizable_field다() {
		AnalyzeView view = ask(FORM_SUMMARY, "휴대전화 번호 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"휴대전화 번호\"}");

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
	}

	@Test
	void 보호자_이름은_완전_일치_문항만_쓰고_이름과_함께_걸리지_않는다() {
		AnalyzeView view = ask(FORM_SUMMARY, "보호자 이름 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"보호자 이름\"}");

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
		List<VersionQuestions> versions = formSummaryVersions();
		TargetMatching.Resolution resolution = TargetMatching.resolve("보호자 이름", versions);
		assertThat(resolution.notes()).containsExactly("보호자 이름");
	}

	@Test
	void 홍보_지원자의_지원_동기는_홍보를_조건으로_되살리고_지원_동기를_요약한다() {
		AnalyzeView view = ask(FORM_SUMMARY, "홍보 지원자의 지원 동기 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.conditions()).containsExactly("홍보");
		assertThat(TargetMatching.resolve(context.target(), formSummaryVersions()).notes()).containsExactly("지원 동기");
		assertThat(view.matched()).allSatisfy(matched -> assertThat(matched.option()).isEqualTo("홍보"));
	}

	@Test
	void 삼학년의_좋았던_점은_학년_문항에_걸리지_않고_3학년을_되살린다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "3학년의 좋았던 점 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"3학년의 좋았던 점\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.conditions()).containsExactly("3학년");
		assertThat(view.result().count()).isEqualTo(7);
	}

	@Test
	void 지원_동기_영상은_영상이_남아_unknown_condition이다() {
		AnalyzeView view = ask(FORM_SUMMARY, "지원 동기 영상 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기 영상\"}");

		assertThat(reason(view)).isEqualTo("unknown_condition");
	}

	@Test
	void 반대_방향_포함은_찾지_않는다() {
		assertThat(TargetMatching.resolve("지원", formSummaryVersions()).notes()).isEmpty();
	}

	@Test
	void context로_보낸_target도_같은_포함_매칭을_쓴다() {
		AnalyzeView view = service(FORM_SUMMARY).analyze(new AnalyzeCommand(OWNER, FORM_ID.toString(),
				"{\"context\":{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"휴대전화 번호\"}}"));

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
	}

	// 요약 실행(SummaryService)도 요약 대상 판정과 같은 포함 매칭으로 문항을 고른다.
	@Test
	void 요약_실행도_포함으로_찾은_문항만_요약한다() {
		SummaryService service = new SummaryService(mock(ThemeClassifier.class), mock(SummaryWriter.class),
				mock(EmbeddingClient.class), new RagSettings(3, 0.2, 3, 0.35, 50, 300, 6, 200));
		Response response = new Response(FORM_ID, null, 1, ResponseStatus.SUBMITTED, List.of(new Answer("q7", "홍보")), true,
				null, Instant.EPOCH);
		ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
		RequestContext context = new RequestContext(List.of("홍보"), null, "summarize", null, "홍보 지원자의 지원 동기", null);

		SummaryService.Outcome outcome = service.summarize(context, formSummaryVersions(), List.of(response));

		assertThat(outcome.unavailableVersions()).isEmpty();
		assertThat(outcome.summary().items()).singleElement()
			.isInstanceOfSatisfying(ThemesItem.class, item -> assertThat(item.target().questionId()).isEqualTo("q9"));
	}

	// 변경 3 — 질의에서 target 찾기

	@Test
	void 좋았던_점_항목의_결과_전체는_target_좋았던_점으로_요약한다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "좋았던 점 항목의 결과 전체를 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.target()).isEqualTo("좋았던 점");
		assertThat(context.conditions()).isEmpty();
		assertThat(context.topic()).isNull();
	}

	@Test
	void 좋았던_점과_아쉬운_점은_ambiguous_match다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "좋았던 점과 아쉬운 점 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\"}");

		assertThat(reason(view)).isEqualTo("ambiguous_match");
		assertThat(view.context().target()).isEqualTo("좋았던 점과 아쉬운 점");
	}

	@Test
	void 질의의_휴대전화는_개인정보_문항이라_unsummarizable_field다() {
		AnalyzeView view = ask(FORM_SUMMARY, "휴대전화 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\"}");

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
		assertThat(view.context().target()).isEqualTo("휴대전화");
	}

	@Test
	void 질의의_지원_분야는_선택형이라_unsummarizable_field다() {
		AnalyzeView view = ask(FORM_SUMMARY, "지원 분야 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\"}");

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
		assertThat(view.context().target()).isEqualTo("지원 분야");
	}

	@Test
	void 조건으로_쓰인_문항_문구는_target을_채우지_않는다() {
		RequestContext parsed = new RequestContext(List.of("면접 희망 날짜 - 응답"), null, "summarize", null, null, null);

		RequestContext filled = QueryText.fillTarget("면접 희망 날짜를 적은 사람들 요약해줘", parsed, formSummaryVersions());

		assertThat(filled.target()).isNull();
	}

	@Test
	void topic이_문항_문구면_target으로_옮겨_주제_요약한다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "좋았던 점 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"좋았던 점\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.target()).isEqualTo("좋았던 점");
		assertThat(context.topic()).isNull();
	}

	@Test
	void topic의_휴대전화는_target으로_옮긴_뒤_unsummarizable_field다() {
		AnalyzeView view = ask(FORM_SUMMARY, "휴대전화 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"휴대전화\"}");

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
		assertThat(view.context().target()).isEqualTo("휴대전화");
		assertThat(view.context().topic()).isNull();
	}

	@Test
	void 지킴_문항_문구가_아닌_topic은_그대로_의견_찾기다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "진행 시간이 길었다는 의견 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"진행 시간이 길었다\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.topic()).isEqualTo("진행 시간이 길었다");
		assertThat(context.target()).isNull();
	}

	@Test
	void 지킴_문항_안의_의견_찾기는_target을_채우고_topic을_유지한다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "좋았던 점 중 강연이 좋았다는 의견 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"강연이 좋았다\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.target()).isEqualTo("좋았던 점");
		assertThat(context.topic()).isEqualTo("강연이 좋았다");
	}

	@SuppressWarnings("unchecked")
	private static List<VersionQuestions> formSummaryVersions() {
		try {
			Map<String, Object> seed = JsonMapper.builder().build().readValue(Files.readString(Path.of(FORM_SUMMARY)), Map.class);
			Map<String, Object> version = ((List<Map<String, Object>>) seed.get("form_versions")).get(0);
			return List.of(VersionQuestions.of(1, (Map<String, Object>) version.get("form")));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
