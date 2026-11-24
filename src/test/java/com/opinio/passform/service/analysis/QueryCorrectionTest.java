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
		RequestContext context = new RequestContext(List.of("홍보"), null, "summarize", null, "지원자의 지원 동기", null);

		SummaryService.Outcome outcome = service.summarize(context, formSummaryVersions(), List.of(response));

		assertThat(outcome.unavailableVersions()).isEmpty();
		assertThat(outcome.summary().items()).singleElement()
			.isInstanceOfSatisfying(ThemesItem.class, item -> assertThat(item.target().questionId()).isEqualTo("q9"));
	}

	// 문항 문구 말고 조사·일반어만 남은 target은 그 문항으로 본다("지원자의"는 일반어 "지원자"에 조사가 붙은 꼴).
	@Test
	void 일반어만_남은_target은_그_문항으로_요약한다() {
		AnalyzeView view = ask(FORM_SUMMARY, "지원자의 지원 동기 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원자의 지원 동기\"}");

		assertThat(view.status()).isEqualTo("ok");
		assertThat(summarized().conditions()).isEmpty();
		assertThat(view.result().count()).isEqualTo(6);
	}

	// 파서가 문항 이름을 target이 아니라 topic에 넣은 꼴(e30·e31의 11-16·11-17 실패 회차). topic이 문항 문구 그 자체면 target으로 옮긴다.

	@Test
	void topic이_문항_문구면_target으로_옮겨_그_문항만_요약한다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "좋았던 점 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"좋았던 점\"}");

		assertThat(view.status()).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.target()).isEqualTo("좋았던 점");
		assertThat(context.topic()).isNull();
		assertThat(context.conditions()).isEmpty();
	}

	@Test
	void 아쉬운_점도_topic에서_target으로_옮긴다() {
		AnalyzeView view = ask(FORM_EVAL_SUMMARY, "아쉬운 점 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"아쉬운 점\"}");

		assertThat(view.status()).isEqualTo("ok");
		assertThat(summarized().target()).isEqualTo("아쉬운 점");
	}

	@Test
	void topic의_휴대전화는_target으로_옮긴_뒤_unsummarizable_field다() {
		AnalyzeView view = ask(FORM_SUMMARY, "휴대전화 요약해줘",
				"{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"휴대전화\"}");

		assertThat(reason(view)).isEqualTo("unsummarizable_field");
		assertThat(view.context().target()).isEqualTo("휴대전화");
		assertThat(view.context().topic()).isNull();
	}

	// 문항 문구에 다른 말이 더 붙은 topic은 의견 찾기라 옮기지 않는다. target이 이미 있거나 요약이 아니어도 옮기지 않는다.
	@Test
	void topic이_문항_문구_말고_다른_말도_담으면_옮기지_않는다() {
		List<VersionQuestions> versions = formEvalSummaryVersions();
		RequestContext withMore = new RequestContext(List.of(), null, "summarize", null, null, "좋았던 점 중 강연이 좋았다");
		RequestContext withTarget = new RequestContext(List.of(), null, "summarize", null, "아쉬운 점", "좋았던 점");
		RequestContext count = new RequestContext(List.of(), null, "count", null, null, "좋았던 점");

		assertThat(QueryText.topicToTarget(withMore, versions)).isEqualTo(withMore);
		assertThat(QueryText.topicToTarget(withTarget, versions)).isEqualTo(withTarget);
		assertThat(QueryText.topicToTarget(count, versions)).isEqualTo(count);
	}

	// 파서가 조건을 target에 섞은 꼴: 파서 조건이 없고, 남은 말이 문항 문구 앞의 선택지 하나이며, 원문에도 다른 말이 없으면
	// 그 선택지를 조건으로, 문항 문구를 target으로 고쳐 진행한다(SPEC 338행).

	@Test
	void 홍보_지원자의_지원_동기는_홍보를_조건으로_지원_동기를_요약한다() {
		assertLifted(FORM_SUMMARY, "홍보 지원자의 지원 동기 요약해줘", "홍보 지원자의 지원 동기", "홍보", "지원 동기", 2);
	}

	@Test
	void 원문이_지원자들의여도_같게_처리한다() {
		assertLifted(FORM_SUMMARY, "홍보 지원자들의 지원 동기 요약해줘", "홍보 지원자의 지원 동기", "홍보", "지원 동기", 2);
	}

	// 사람 결정(2026-11-25): "홍보 지원 동기"는 지원 분야가 홍보인 응답의 지원 동기로 본다.
	@Test
	void 홍보_지원_동기는_지원_분야가_홍보인_응답의_지원_동기다() {
		assertLifted(FORM_SUMMARY, "홍보 지원 동기 요약해줘", "홍보 지원 동기", "홍보", "지원 동기", 2);
	}

	@Test
	void 삼학년의_좋았던_점은_3학년을_조건으로_좋았던_점을_요약한다() {
		assertLifted(FORM_EVAL_SUMMARY, "3학년의 좋았던 점 요약해줘", "3학년의 좋았던 점", "3학년", "좋았던 점", 7);
	}

	// 고친 context(조건 "홍보", target "지원 동기")를 버튼으로 다시 보내도 같은 대상을 센다.
	@Test
	void 고친_context를_버튼으로_다시_보내도_같은_대상을_센다() {
		AnalyzeView again = service(FORM_SUMMARY).analyze(new AnalyzeCommand(OWNER, FORM_ID.toString(),
				"{\"context\":{\"conditions\":[\"홍보\"],\"operation\":\"summarize\",\"target\":\"지원 동기\"}}"));

		assertThat(again.status()).isEqualTo("ok");
		assertThat(again.result().count()).isEqualTo(2);
	}

	// 하나라도 어긋나면 고치지 않고 되묻는다. 숫자를 내지 않는다.
	@Test
	void 원문이나_파서_출력이_조건에_어긋나면_고치지_않고_되묻는다() {
		String[][] cases = {
				// 파서가 원문의 "미"·"재"를 빼먹음: 원문에 "미지원자의"가 남는다
				{ "홍보 미지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				{ "홍보 재지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				// 원문에 부정 표현이 있음
				{ "홍보 아닌 지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				// 원문에 범위를 좁히는 말이 있음(앞 단계가 "오늘"을 조건으로 되살린다)
				{ "오늘 홍보 지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				// 선택지가 문항 문구 뒤에 있음: 지원 동기 가운데 홍보 이야기일 수 있다
				{ "지원 동기에서 홍보 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"지원 동기에서 홍보\"}" },
				// 선택지가 둘
				{ "홍보 기획 지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 기획 지원자의 지원 동기\"}" },
				// 일반어가 아닌 말이 낌
				{ "홍보 쪽 지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 쪽 지원자의 지원 동기\"}" },
				// 파서가 "홍보"를 부정 조건으로 잘못 냄: 파서 조건이 있으면 고치지 않는다
				{ "홍보 지원자의 지원 동기 요약해줘",
						"{\"conditions\":[\"홍보\"],\"match\":\"not\",\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				// 파서가 원문에 없는 선택지를 target에 넣음: 사용자는 "홍보"를 말한 적이 없다
				{ "지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				{ "지원자의 지원 동기 요약해줘", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				// 파서가 원문에 없는 조건을 all로 냄: 고치면 파서 조건("경영학과")을 버리게 되므로 고치지 않는다
				{ "홍보 지원자의 지원 동기 요약해줘",
						"{\"conditions\":[\"경영학과\"],\"match\":\"all\",\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" },
				// 파서가 이미 조건을 냄
				{ "소프트웨어학과 홍보 지원자의 지원 동기 요약해줘",
						"{\"conditions\":[\"소프트웨어학과\"],\"match\":\"all\",\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}" } };
		for (String[] c : cases) {
			AnalyzeView view = ask(FORM_SUMMARY, c[0], c[1]);

			assertThat(view.status()).as(c[0]).isEqualTo("clarify");
			assertThat(view.result()).as(c[0]).isNull();
		}
		verify(summaries, never()).summarize(any(), anyList(), anyList());
	}

	// 원문 대조: 일반어여도 이 폼의 선택지("학생")면 파서가 빼먹은 조건일 수 있으므로 남은 말로 센다.
	@Test
	void 원문에_남은_일반어가_선택지면_남은_말로_센다() {
		List<VersionQuestions> versions = List.of(VersionQuestions.of(1, Map.of("sections", List.of(Map.of("section_id", "s1",
				"questions", List.of(
						Map.of("question_id", "q1", "question_note", "직업", "question_type", "multiple_choice", "options",
								List.of("학생", "직장인")),
						Map.of("question_id", "q2", "question_note", "지원 분야", "question_type", "multiple_choice", "options",
								List.of("기획", "홍보")),
						Map.of("question_id", "q3", "question_note", "지원 동기", "question_type", "long_answer", "options",
								List.of())))))));

		assertThat(QueryText.residualWords("학생 홍보 지원자의 지원 동기 요약해줘", List.of("지원 동기", "홍보"), versions))
			.containsExactly("학생");
		assertThat(QueryText.residualWords("홍보 지원자들의 지원 동기 요약해줘", List.of("지원 동기", "홍보"), versions)).isEmpty();
	}

	// 일반어와 글자가 같은 선택지("학생")는 꺼내지 않는다. 그 폼에서는 되묻기로 간다(SPEC 338행).
	@Test
	void 일반어와_같은_선택지는_조건으로_꺼내지_않는다() {
		List<VersionQuestions> versions = List.of(jobVersion(List.of("학생", "직장인")));

		assertThat(TargetMatching.lift("학생의 지원 동기", versions)).isEmpty();
		assertThat(TargetMatching.lift("학생 지원 동기", versions)).isEmpty();
		assertThat(TargetMatching.lift("직장인의 지원 동기", versions)).hasValueSatisfying(lift -> {
			assertThat(lift.note()).isEqualTo("지원 동기");
			assertThat(lift.options()).containsExactly("직장인");
		});
	}

	private void assertLifted(String fixture, String query, String target, String option, String note, int count) {
		AnalyzeView view = ask(fixture, query,
				"{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"" + target + "\"}");

		assertThat(view.status()).as(query).isEqualTo("ok");
		RequestContext context = summarized();
		assertThat(context.conditions()).containsExactly(option);
		assertThat(context.target()).isEqualTo(note);
		assertThat(view.result().count()).isEqualTo(count);
	}

	// 파서가 부정·"또는" 조건을 낸 질의에서 target 안의 선택지를 조건에 섞어 세지 않는다. target 글자가 원문과 달라도, 줄어들어도 같다.
	// PR #156 리뷰에서 not(경영학과 ∨ 홍보)로 계산되어 rs01·rs03·rs06이 나오던 질의다(의도는 rs05).
	@Test
	void not과_any에_target의_선택지가_섞인_질의는_숫자를_내지_않고_되묻는다() {
		String[][] cases = {
				{ "경영학과 아닌 홍보 지원자의 지원 동기 요약해줘", "경영학과", "not", "홍보 지원자의 지원 동기" },
				{ "경영학과 아닌 홍보 지원자들의 지원 동기 요약해줘", "경영학과", "not", "홍보 지원자의 지원 동기" },
				{ "경영학과 아닌 홍보 지원자들의 지원 동기 요약해줘", "경영학과", "not", "홍보 지원 동기" },
				{ "경영학과이거나 소프트웨어학과인 홍보 지원자의 지원 동기 요약해줘", "경영학과\",\"소프트웨어학과", "any", "홍보 지원자의 지원 동기" } };
		for (String[] c : cases) {
			AnalyzeView view = ask(FORM_SUMMARY, c[0], "{\"conditions\":[\"" + c[1] + "\"],\"match\":\"" + c[2]
					+ "\",\"operation\":\"summarize\",\"target\":\"" + c[3] + "\"}");

			assertThat(view.status()).as(c[0] + " / " + c[3]).isEqualTo("clarify");
			assertThat(view.result()).isNull();
		}
		verify(summaries, never()).summarize(any(), anyList(), anyList());
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

	// PR #156 리뷰 반영
	// {context}는 보낸 조건대로 센다. target 나머지의 선택지를 조건으로 되살리지 않는다(SPEC 319행).

	@Test
	void context의_target_나머지_선택지는_조건으로_더하지_않고_unknown_condition이다() {
		AnalyzeView view = service(FORM_SUMMARY).analyze(new AnalyzeCommand(OWNER, FORM_ID.toString(),
				"{\"context\":{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"홍보 지원자의 지원 동기\"}}"));

		assertThat(reason(view)).isEqualTo("unknown_condition");
		verify(summaries, never()).summarize(any(), anyList(), anyList());
	}

	// target은 요약에서만 쓴다. 목록·건수 질의는 이번 변경과 상관없이 파서의 조건대로 센다.
	@Test
	void 목록_질의는_target_보정과_상관없이_조건대로_센다() {
		AnalyzeView list = ask(FORM_SUMMARY, "학과 문항에서 소프트웨어학과를 보여줘",
				"{\"conditions\":[\"소프트웨어학과\"],\"operation\":\"list\"}");
		AnalyzeView count = ask(FORM_SUMMARY, "소프트웨어학과 몇 명이야", "{\"conditions\":[\"소프트웨어학과\"],\"operation\":\"count\"}");

		assertThat(list.status()).isEqualTo("ok");
		assertThat(list.result().count()).isEqualTo(4);
		assertThat(count.status()).isEqualTo("ok");
		assertThat(count.result().count()).isEqualTo(4);
	}

	// 여러 문항 문구를 이은 target 사이에 다른 낱말이 끼면 문항을 찾지 못한 것으로 본다(SPEC 338행).

	@Test
	void 리뷰3_버전마다_문구가_다른_문항을_이은_target_사이에_선택지가_끼면_찾지_못한_것이다() {
		List<VersionQuestions> versions = List.of(motiveVersion(1, "지원 동기"), motiveVersion(2, "참여 동기"));

		assertThat(TargetMatching.resolve("지원 동기와 홍보 참여 동기", versions).notes()).isEmpty();
		assertThat(TargetMatching.resolve("지원 동기와 참여 동기", versions).notes()).containsExactlyInAnyOrder("지원 동기",
				"참여 동기");
	}

	// 일반어("학생")가 이 폼의 선택지이기도 하면 target 나머지에 남아도 버려지는 말이 아니라 조건이다. 문항을 찾지 못한 것으로 본다.
	@Test
	void target에_남은_일반어가_이_폼의_선택지면_문항을_찾지_못한_것이다() {
		List<VersionQuestions> withOption = List.of(jobVersion(List.of("학생", "직장인")));
		List<VersionQuestions> withoutOption = List.of(jobVersion(List.of("재직자", "직장인")));

		assertThat(TargetMatching.resolve("학생들의 지원 동기", withOption).notes()).isEmpty();
		assertThat(TargetMatching.resolve("학생 지원 동기", withOption).notes()).isEmpty();
		assertThat(TargetMatching.resolve("학생들의 지원 동기", withoutOption).notes()).containsExactly("지원 동기");
	}

	// 빈도 말이 여러 낱말 선택지의 일부면 빼지 않는다.

	@Test
	void 여러_낱말_선택지를_이루는_빈도_말은_빼지_않는다() {
		List<VersionQuestions> versions = List.of(choiceVersion("참여 빈도", List.of("가장 많이", "가끔")));
		RequestContext parsed = new RequestContext(List.of(), null, "count", null, null, null);

		assertThat(QueryText.reviveDroppedConditions("가장 많이 몇 명?", parsed, versions).conditions())
			.containsExactly("가장", "많이");
	}

	private static VersionQuestions jobVersion(List<String> jobOptions) {
		return VersionQuestions.of(1, Map.of("sections", List.of(Map.of("section_id", "s1", "questions",
				List.of(Map.of("question_id", "q1", "question_note", "직업", "question_type", "multiple_choice", "options", jobOptions),
						Map.of("question_id", "q2", "question_note", "지원 동기", "question_type", "long_answer", "options",
								List.of()))))));
	}

	private static VersionQuestions choiceVersion(String note, List<String> options) {
		return VersionQuestions.of(1, Map.of("sections", List.of(Map.of("section_id", "s1", "questions", List.of(Map.of(
				"question_id", "q1", "question_note", note, "question_type", "multiple_choice", "options", options))))));
	}

	private static VersionQuestions motiveVersion(int formVersion, String motiveNote) {
		return VersionQuestions.of(formVersion, Map.of("sections", List.of(Map.of("section_id", "s1", "questions",
				List.of(Map.of("question_id", "q7", "question_note", "지원 분야", "question_type", "multiple_choice", "options",
						List.of("기획", "홍보")),
						Map.of("question_id", "q9", "question_note", motiveNote, "question_type", "long_answer", "options",
								List.of()))))));
	}

	@SuppressWarnings("unchecked")
	private static List<VersionQuestions> formEvalSummaryVersions() {
		try {
			Map<String, Object> seed = JsonMapper.builder().build().readValue(Files.readString(Path.of(FORM_EVAL_SUMMARY)), Map.class);
			Map<String, Object> version = ((List<Map<String, Object>>) seed.get("form_versions")).get(0);
			return List.of(VersionQuestions.of(1, (Map<String, Object>) version.get("form")));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
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
