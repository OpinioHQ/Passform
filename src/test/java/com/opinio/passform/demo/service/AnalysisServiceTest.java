package com.opinio.passform.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.opinio.passform.demo.config.AnalysisProperties;
import com.opinio.passform.demo.domain.ForbiddenException;
import com.opinio.passform.demo.domain.InvalidRequestException;
import com.opinio.passform.demo.domain.analysis.RequestContextValidator;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.FormJson;
import com.opinio.passform.demo.domain.form.FormStatus;
import com.opinio.passform.demo.domain.response.Response;
import com.opinio.passform.demo.llm.ConditionLlmMatcher;
import com.opinio.passform.demo.llm.LlmClient;
import com.opinio.passform.demo.llm.LlmUnavailableException;
import com.opinio.passform.demo.llm.QueryParser;
import com.opinio.passform.demo.llm.SummaryWriter;
import com.opinio.passform.demo.llm.ThemeClassifier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// LLM은 모의 객체로 바꾼다(AGENTS 5절). 폼은 data/seed의 골든 fixture를 읽기만 한다.
class AnalysisServiceTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ScriptedLlm llm = new ScriptedLlm();

	private AnalysisService service(FormSnapshot snapshot, AnalysisProperties properties) {
		FormSnapshotLoader loader = mock(FormSnapshotLoader.class);
		when(loader.loadOwned(any(), any())).thenReturn(snapshot);
		RequestContextValidator validator = new RequestContextValidator();
		SummaryService summaries = new SummaryService(llm, new ThemeClassifier(llm, properties),
				new SummaryWriter(llm, properties), properties);
		return new AnalysisService(loader, new QueryParser(llm, validator, properties), validator,
				new ConditionMatchingService(new ConditionLlmMatcher(llm, properties)), summaries);
	}

	private AnalysisResponse ask(String fixture, String query) {
		return service(seed(fixture, true), AnalysisProperties.defaults()).analyze("f", "owner", query, null);
	}

	private AnalysisResponse context(String fixture, String json) {
		return service(seed(fixture, true), AnalysisProperties.defaults()).analyze("f", "owner", null, JSON.readTree(json));
	}

	// ---- 범위·통계·파서

	@Test
	void AC77_범위_표현은_파서를_부르지_않고_안내한다() {
		AnalysisResponse response = ask("form_a_versions", "버전 1만 보여줘");
		assertThat(response.clarify().reason()).isEqualTo("unsupported_scope");
		assertThat(response.result()).isNull();
		assertThat(llm.calls("parse_query")).isZero();
	}

	@Test
	void AC27_operation이_null이면_되묻고_파서는_한_번만_부른다() {
		llm.parser(query -> "{\"conditions\":[],\"operation\":null}");
		AnalysisResponse response = ask("form_a_versions", "응답 결과를 알려줘");
		assertThat(response.clarify().reason()).isEqualTo("missing_operation");
		assertThat(llm.calls("parse_query")).isEqualTo(1);
	}

	@Test
	void AC27_요약이_꺼진_폼은_선택지에_요약을_넣지_않는다() {
		llm.parser(query -> "{\"conditions\":[],\"operation\":null}");
		AnalysisResponse response = service(seed("form_a_versions", false), AnalysisProperties.defaults()).analyze("f",
				"owner", "응답 결과를 알려줘", null);
		assertThat(response.clarify().options()).isEqualTo(List.of("count", "list"));
	}

	@Test
	void AC27_통계를_파서가_요약으로_잘못_해석해도_결과를_내지_않는다() {
		llm.parser(query -> "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"통계\",\"topic\":\"학과별\"}");
		AnalysisResponse response = ask("form_a_versions", "학과별 통계 보여줘");
		assertThat(response.clarify().reason()).isEqualTo("missing_operation");
		assertThat(response.result()).isNull();
		assertThat(llm.calls("classify_themes")).isZero();
	}

	@Test
	void AC30_형식_오류는_한_번만_다시_부르고_실패하면_되묻는다() {
		llm.parser(query -> "{\"conditions\":[],\"operation\":\"count\",\"group_by\":\"학년\"}");
		AnalysisResponse response = ask("form_a_versions", "전체 응답 수 알려줘");
		assertThat(response.clarify().reason()).isEqualTo("parse_failed");
		assertThat(llm.calls("parse_query")).isEqualTo(2);
	}

	@Test
	void AC30_한_번_실패_후_정상이면_결과를_낸다() {
		llm.parser(query -> query.contains("이전 출력") ? "{\"conditions\":[],\"operation\":\"count\"}" : "JSON 아님");
		AnalysisResponse response = ask("form_a_versions", "전체 응답 수 알려줘");
		assertThat(response.status()).isEqualTo("ok");
		assertThat(response.result().count()).isEqualTo(9);
		assertThat(llm.calls("parse_query")).isEqualTo(2);
	}

	@Test
	void LLM_장애는_숫자로_바꾸지_않고_예외로_드러낸다() {
		llm.fail = true;
		assertThatThrownBy(() -> ask("form_a_versions", "전체 응답 수 알려줘")).isInstanceOf(LlmUnavailableException.class);
	}

	// ---- 파서가 조건을 버려도 전체 수가 나가지 않는다(실제 출력에서 찾은 문제)

	@Test
	void 파서가_조건을_버려도_전체_건수를_내지_않고_되묻는다() {
		llm.parser(query -> "{\"conditions\":[],\"match\":\"all\",\"operation\":\"count\"}");
		llm.matcher(conditions -> "NONE");
		AnalysisResponse response = ask("form_a_versions", "수의학과 학생 수 알려줘");
		assertThat(response.clarify().reason()).isEqualTo("unknown_condition");
		assertThat(response.result()).isNull();
	}

	// ---- 조건 판정(AC78, AC22, AC23, AC24)

	@Test
	void AC78_버전별로_판정하고_판정_불가를_coverage에_표시한다() {
		AnalysisResponse response = context("form_a_versions", "{\"conditions\":[\"소프트웨어학과\"],\"operation\":\"list\"}");
		assertThat(response.result().count()).isEqualTo(5);
		assertThat(response.result().responseIds()).containsExactly("ra01", "ra02", "ra03", "ra05", "ra08");
		assertThat(response.coverage().totalSubmittedCount()).isEqualTo(9);
		assertThat(response.coverage().notEvaluableCount()).isEqualTo(4);
		assertThat(response.coverage().notEvaluableByVersion()).singleElement().satisfies(version -> {
			assertThat(version.formVersion()).isEqualTo(3);
			assertThat(version.unavailableConditions()).containsExactly("소프트웨어학과");
		});
		assertThat(response.matched()).extracting(AnalysisResponse.MatchedEvidence::formVersion).containsExactly(1, 2);
		assertThat(response.matched()).extracting(AnalysisResponse.MatchedEvidence::questionNote).doesNotContainNull();
		assertThat(llm.calls("parse_query")).isZero();
	}

	@Test
	void AC78_부정_조건에도_판정_불가를_넣지_않는다() {
		AnalysisResponse response = context("form_a_versions", "{\"conditions\":[\"소프트웨어학과\"],\"match\":\"not\",\"operation\":\"count\"}");
		assertThat(response.result().count()).isZero();
		assertThat(response.result().responseIds()).isNull();
		assertThat(response.coverage().notEvaluableCount()).isEqualTo(4);
	}

	@Test
	void AC22_어느_버전에도_없는_조건은_0건이_아니라_되묻는다() {
		llm.matcher(conditions -> "NONE");
		AnalysisResponse response = context("form_a_versions", "{\"conditions\":[\"수의학과\"],\"operation\":\"count\"}");
		assertThat(response.clarify().reason()).isEqualTo("unknown_condition");
		assertThat(response.result()).isNull();
		assertThat(response.clarify().options()).isNotEmpty();
	}

	@Test
	void AC25_같은_표현이_다른_문항에_걸리면_되묻는다() {
		AnalysisResponse response = context("form_b_mixed", "{\"conditions\":[\"경영학과\"],\"operation\":\"count\"}");
		assertThat(response.clarify().reason()).isEqualTo("ambiguous_match");
		assertThat(response.clarify().options()).hasSize(3);
		assertThat(llm.calls("match_conditions")).isZero();
	}

	@Test
	void AC25_단일_선택_all은_되묻고_checkbox_all은_계산한다() {
		AnalysisResponse single = context("form_b_mixed", "{\"conditions\":[\"소프트웨어학과\",\"컴퓨터공학과\"],\"match\":\"all\",\"operation\":\"count\"}");
		assertThat(single.clarify().reason()).isEqualTo("ambiguous_match");
		assertThat(single.clarify().options()).isEqualTo(List.of("any"));

		AnalysisResponse checkbox = context("form_b_mixed", "{\"conditions\":[\"기획\",\"디자인\"],\"match\":\"all\",\"operation\":\"list\"}");
		assertThat(checkbox.status()).isEqualTo("ok");
		assertThat(checkbox.result().responseIds()).contains("rb06");
	}

	@Test
	void AC26_조건이_둘인데_match가_없으면_되묻는다() {
		AnalysisResponse response = context("form_b_mixed", "{\"conditions\":[\"소프트웨어학과\",\"4학년\"],\"operation\":\"count\"}");
		assertThat(response.clarify().reason()).isEqualTo("missing_match");
		assertThat(response.clarify().options()).isEqualTo(List.of("all", "any"));
	}

	@Test
	void AC26_긍정_부정이_섞이면_요청을_나누라고_안내한다() {
		llm.parser(query -> "{\"conditions\":[\"소프트웨어학과가 아닌\",\"4학년\"],\"match\":\"all\",\"operation\":\"count\"}");
		AnalysisResponse response = ask("form_b_mixed", "소프트웨어학과가 아닌 4학년 수");
		assertThat(response.clarify().reason()).isEqualTo("missing_match");
		assertThat(response.clarify().options()).isEmpty();
		assertThat(response.result()).isNull();
	}

	@Test
	void AC29_미응답과_not이_함께_오면_되묻는다() {
		AnalysisResponse response = context("form_b_mixed", "{\"conditions\":[\"자기소개 - 미응답\"],\"match\":\"not\",\"operation\":\"count\"}");
		assertThat(response.clarify().reason()).isEqualTo("double_negation");
	}

	@Test
	void AC32_context가_스키마에_맞지_않으면_거부한다() {
		assertThatThrownBy(() -> context("form_a_versions", "{\"conditions\":[],\"operation\":\"statistics\"}"))
			.isInstanceOfSatisfying(InvalidRequestException.class,
					exception -> assertThat(exception.getMessage()).isEqualTo("invalid_context"));
		assertThatThrownBy(() -> context("form_a_versions", "{\"conditions\":[],\"operation\":\"count\",\"form_versions\":[1]}"))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void query와_context를_같이_보내거나_둘_다_없으면_거부한다() {
		AnalysisService service = service(seed("form_a_versions", true), AnalysisProperties.defaults());
		assertThatThrownBy(() -> service.analyze("f", "owner", null, null)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.analyze("f", "owner", "질문", JSON.readTree("{}"))).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void 소유자가_아니면_분석할_수_없다() {
		FormSnapshotLoader loader = mock(FormSnapshotLoader.class);
		when(loader.loadOwned(any(), any())).thenThrow(new ForbiddenException());
		AnalysisProperties properties = AnalysisProperties.defaults();
		RequestContextValidator validator = new RequestContextValidator();
		AnalysisService service = new AnalysisService(loader, new QueryParser(llm, validator, properties), validator,
				new ConditionMatchingService(new ConditionLlmMatcher(llm, properties)),
				new SummaryService(llm, new ThemeClassifier(llm, properties), new SummaryWriter(llm, properties), properties));
		assertThatThrownBy(() -> service.analyze("f", "other", "전체 응답 수", null)).isInstanceOf(ForbiddenException.class);
		assertThat(llm.calls("parse_query")).isZero();
	}

	// ---- 정렬

	@Test
	void AC28_정렬할_수_없는_문항은_되묻고_없는_문항은_unknown이다() {
		AnalysisResponse text = context("form_b_mixed", "{\"conditions\":[],\"operation\":\"list\",\"sort\":{\"by\":\"자기소개\",\"order\":\"asc\"}}");
		assertThat(text.clarify().reason()).isEqualTo("unsortable_field");

		AnalysisResponse missing = context("form_b_mixed", "{\"conditions\":[],\"operation\":\"list\",\"sort\":{\"by\":\"키\",\"order\":\"asc\"}}");
		assertThat(missing.clarify().reason()).isEqualTo("unknown_condition");
	}

	@Test
	void AC61_count에_붙은_sort는_무시한다() {
		AnalysisResponse response = context("form_b_mixed", "{\"conditions\":[],\"operation\":\"count\",\"sort\":{\"by\":\"자기소개\",\"order\":\"asc\"}}");
		assertThat(response.status()).isEqualTo("ok");
		assertThat(response.result().count()).isEqualTo(14);
	}

	@Test
	void AC61_선택지_순서로_정렬하고_문항_없는_버전을_알린다() {
		AnalysisResponse response = context("form_a_versions", "{\"conditions\":[],\"operation\":\"list\",\"sort\":{\"by\":\"학과\",\"order\":\"asc\"}}");
		assertThat(response.status()).isEqualTo("ok");
		assertThat(response.result().responseIds()).hasSize(9);
		assertThat(response.coverage().sortUnavailableByVersion()).containsExactly(3);
	}

	@Test
	void AC28_버전_사이에_선택지_문구가_바뀐_문항은_정렬하지_않는다() {
		AnalysisResponse response = context("form_b_mixed", "{\"conditions\":[],\"operation\":\"list\",\"sort\":{\"by\":\"소속 학과\",\"order\":\"asc\"}}");
		assertThat(response.clarify().reason()).isEqualTo("unsortable_field");
	}

	// ---- 요약(AC86~AC93)

	@Test
	void AC31_응답_원문의_지시문은_건수와_목록을_바꾸지_않는다() {
		AnalysisResponse response = context("form_b_mixed", "{\"conditions\":[\"소프트웨어학과\"],\"operation\":\"list\"}");
		assertThat(response.result().responseIds()).doesNotContain("rb11");
	}

	@Test
	void AC93_일치_0건이면_LLM을_부르지_않는다() {
		AnalysisResponse response = context("form_a_versions", "{\"conditions\":[\"소프트웨어학과\"],\"match\":\"not\",\"operation\":\"summarize\"}");
		assertThat(response.result().summary().status()).isEqualTo("no_matches");
		assertThat(response.result().count()).isZero();
		assertThat(llm.totalCalls()).isZero();
	}

	@Test
	void AC93_모두_미동의면_LLM을_부르지_않고_건수는_유지한다() {
		FormSnapshot snapshot = seedWithConsent("form_a_versions", id -> false);
		AnalysisResponse response = service(snapshot, AnalysisProperties.defaults()).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\"}"));
		assertThat(response.result().summary().status()).isEqualTo("no_consented_responses");
		assertThat(response.result().count()).isEqualTo(9);
		assertThat(response.result().summary().excludedNoConsentCount()).isEqualTo(9);
		assertThat(llm.totalCalls()).isZero();
	}

	@Test
	void AC93_상한을_넘으면_LLM을_부르지_않는다() {
		AnalysisProperties small = new AnalysisProperties("m", "m", "m", 50, 3, 6, 3, 0.2, 0.35);
		AnalysisResponse response = service(seed("form_a_versions", true), small).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\"}"));
		assertThat(response.result().summary().status()).isEqualTo("too_many_responses");
		assertThat(response.result().summary().items()).isEmpty();
		assertThat(llm.totalCalls()).isZero();
	}

	@Test
	void AC92_개인정보_문항_요약은_되묻고_선택형도_되묻는다() {
		AnalysisResponse personal = context("form_b_mixed", "{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"소속 학과\"}");
		assertThat(personal.clarify().reason()).isEqualTo("unsummarizable_field");
		assertThat(llm.totalCalls()).isZero();
	}

	@Test
	void AC86_AC87_AC88_주제_요약은_코드가_세고_미동의_응답을_보내지_않는다() {
		FormSnapshot snapshot = seedWithConsent("form_b_mixed", id -> !id.equals("rb01"));
		llm.classifier(answer -> answer.contains("좋아") ? "A" : "B");
		AnalysisResponse response = service(snapshot, AnalysisProperties.defaults()).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"자기소개\"}"));
		assertThat(response.status()).isEqualTo("ok");
		assertThat(response.result().count()).isEqualTo(14);
		AnalysisResponse.SummaryView summary = response.result().summary();
		assertThat(summary.excludedNoConsentCount()).isEqualTo(1);
		assertThat(summary.items()).singleElement().satisfies(item -> {
			assertThat(item.type()).isEqualTo("themes");
			assertThat(item.themes()).isNotEmpty().allSatisfy(theme -> {
				assertThat(theme.responseIds()).doesNotContain("rb01");
				assertThat(theme.count()).isEqualTo(theme.responseIds().size());
				assertThat(theme.evidence()).allSatisfy(evidence -> assertThat(theme.responseIds()).contains(evidence.responseId()));
			});
		});
		assertThat(llm.requestsText()).doesNotContain("백엔드 개발을 좋아합니다.");
	}

	@Test
	void AC86_문항이_없는_버전은_요약에서_빼고_수를_알린다() {
		FormSnapshot snapshot = seedWithConsent("form_a_versions", id -> true);
		llm.classifier(answer -> "A");
		AnalysisResponse response = service(snapshot, AnalysisProperties.defaults()).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\",\"target\":\"소속 학과\"}"));
		assertThat(response.status()).isEqualTo("clarify");
	}

	@Test
	void AC89_의견_요약은_개수가_없고_근거가_없으면_문장도_없다() {
		FormSnapshot snapshot = seedWithConsent("form_b_mixed", id -> true);
		llm.lowSimilarity = true;
		AnalysisResponse response = service(snapshot, AnalysisProperties.defaults()).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"비용이 부담된다\"}"));
		AnalysisResponse.SummaryView summary = response.result().summary();
		assertThat(summary.status()).isEqualTo("insufficient_evidence");
		assertThat(summary.topic().text()).isNull();
		assertThat(summary.topic().evidence()).isEmpty();
		assertThat(summary.items()).isEmpty();
		assertThat(llm.calls("summarize")).isZero();
		assertThat(response.result().count()).isEqualTo(14);
	}

	@Test
	void AC89_근거가_있으면_요약문을_쓰고_건수는_그대로다() {
		FormSnapshot snapshot = seedWithConsent("form_b_mixed", id -> true);
		AnalysisResponse response = service(snapshot, AnalysisProperties.defaults()).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\",\"topic\":\"경험\"}"));
		AnalysisResponse.SummaryView summary = response.result().summary();
		assertThat(summary.status()).isEqualTo("ok");
		assertThat(summary.topic().text()).isEqualTo("요약문");
		assertThat(summary.topic().evidence()).hasSizeLessThanOrEqualTo(3);
		assertThat(response.result().count()).isEqualTo(14);
	}

	@Test
	void 숫자만_있는_단답은_전체_요약에서_뺀다() {
		FormDefinition definition = FormJson.parseDefinition("""
				{"title":"t","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"age","question_note":"나이","question_type":"short_answer"},
				 {"question_id":"why","question_note":"동기","question_type":"long_answer"},
				 {"question_id":"grade","question_note":"학년","question_type":"multiple_choice","options":["1학년","2학년"]}]}]}""");
		Response first = new Response("r1", "f", null, 1, ResponseStatus.SUBMITTED,
				"[{\"question_id\":\"age\",\"value\":\"19\"},{\"question_id\":\"why\",\"value\":\"배우고 싶어요\"},{\"question_id\":\"grade\",\"value\":\"1학년\"}]",
				true, Instant.parse("2026-10-01T00:00:00Z"), null);
		FormSnapshot snapshot = snapshot(Map.of(1, definition), List.of(first), true);
		llm.classifier(answer -> "학습");
		AnalysisResponse response = service(snapshot, AnalysisProperties.defaults()).analyze("f", "owner", null,
				JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\"}"));
		List<AnalysisResponse.SummaryItem> items = response.result().summary().items();
		assertThat(items).extracting(item -> item.target().questionNote()).containsExactly("동기", "학년");
		assertThat(items.get(1).options().get(0).count()).isEqualTo(1);
		assertThat(llm.requestsText()).doesNotContain("\"19\"");
	}

	@Test
	void AC92_답_속_연락처를_가리고_개인정보_문항은_보내지_않는다() {
		FormDefinition definition = FormJson.parseDefinition("""
				{"title":"t","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"name","question_note":"이름","question_type":"short_answer","input_format":{"kind":"name"}},
				 {"question_id":"phone","question_note":"연락처","question_type":"short_answer","input_format":{"kind":"mobile_phone"}},
				 {"question_id":"birth","question_note":"생일","question_type":"short_answer","input_format":{"kind":"date"},"profile_key":"birth_date"},
				 {"question_id":"why","question_note":"동기","question_type":"long_answer"}]}]}""");
		Response response = new Response("r1", "f", null, 1, ResponseStatus.SUBMITTED,
				"[{\"question_id\":\"name\",\"value\":\"홍길동\"},{\"question_id\":\"phone\",\"value\":\"01099998888\"},{\"question_id\":\"birth\",\"value\":\"2005-05-05\"},"
						+ "{\"question_id\":\"why\",\"value\":\"연락은 010-1234-5678, a@example.com으로\"}]",
				true, Instant.parse("2026-10-01T00:00:00Z"), null);
		llm.classifier(answer -> "연락");
		service(snapshot(Map.of(1, definition), List.of(response), true), AnalysisProperties.defaults()).analyze("f", "owner",
				null, JSON.readTree("{\"conditions\":[],\"operation\":\"summarize\"}"));
		assertThat(llm.requestsText()).doesNotContain("홍길동", "01099998888", "2005-05-05", "010-1234-5678", "a@example.com");
		assertThat(llm.calls("classify_themes")).isEqualTo(1);
	}

	// ---- 준비

	private static FormSnapshot seed(String fixture, boolean aiSummary) {
		return seedWith(fixture, aiSummary, id -> true);
	}

	private static FormSnapshot seedWithConsent(String fixture, Function<String, Boolean> consent) {
		return seedWith(fixture, true, consent);
	}

	private static FormSnapshot seedWith(String fixture, boolean aiSummary, Function<String, Boolean> consent) {
		try {
			JsonNode root = JSON.readTree(Files.readString(Path.of("data/seed/forms/" + fixture + ".json")));
			Map<Integer, FormDefinition> definitions = new TreeMap<>();
			for (JsonNode version : root.path("form_versions")) {
				definitions.put(version.path("form_version").asInt(), FormJson.parseDefinition(JSON.writeValueAsString(version.path("form"))));
			}
			List<Response> responses = new ArrayList<>();
			for (JsonNode response : root.path("responses")) {
				if (!"submitted".equals(response.path("status").asString())) {
					continue;
				}
				String id = response.path("response_id").asString();
				responses.add(new Response(id, "f", null, response.path("form_version").asInt(), ResponseStatus.SUBMITTED,
						JSON.writeValueAsString(response.path("answers")), consent.apply(id),
						Instant.parse(response.path("submitted_at").asString()), null));
			}
			return snapshot(definitions, responses, aiSummary);
		}
		catch (java.io.IOException exception) {
			throw new java.io.UncheckedIOException(exception);
		}
	}

	private static FormSnapshot snapshot(Map<Integer, FormDefinition> definitions, List<Response> responses, boolean aiSummary) {
		Form form = new Form("f", "owner", "f", FormStatus.OPEN, "t", 1, null, null, false, false, aiSummary, "c", "all_questions",
				Instant.parse("2026-10-01T00:00:00Z"));
		List<ResponseRecord> records = responses.stream()
			.map(response -> new ResponseRecord(response.getId(), response.getFormVersion(), response.getStatus(),
					FormJson.parseAnswers(response.getAnswersJson())))
			.toList();
		return new FormSnapshot(form, definitions, responses, records);
	}

	private static final class ScriptedLlm implements LlmClient {

		private static final Pattern ANSWER = Pattern.compile("\"(a\\d+)\":\"((?:[^\"\\\\]|\\\\.)*)\"");

		private final List<Request> requests = new java.util.concurrent.CopyOnWriteArrayList<>();

		private final List<String> embedded = new java.util.concurrent.CopyOnWriteArrayList<>();

		private Function<String, String> parser = query -> "{\"conditions\":[],\"operation\":\"count\"}";

		private Function<String, String> matcher = conditions -> "NONE";

		private Function<String, String> classifier = answer -> "주제";

		private boolean fail;

		private boolean lowSimilarity;

		void parser(Function<String, String> parser) {
			this.parser = parser;
		}

		void matcher(Function<String, String> matcher) {
			this.matcher = matcher;
		}

		void classifier(Function<String, String> classifier) {
			this.classifier = classifier;
		}

		@Override
		public List<String> complete(Request request) {
			if (fail) {
				throw new LlmUnavailableException("모의 장애");
			}
			requests.add(request);
			return switch (request.purpose()) {
				case "parse_query" -> List.of(parser.apply(request.input()));
				case "match_conditions" -> List.of(matcherOutput(request));
				case "classify_themes" -> List.of(classifierOutput(request));
				default -> List.of("{\"text\":\"요약문\"}");
			};
		}

		@Override
		public List<float[]> embed(String purpose, List<String> inputs) {
			if (fail) {
				throw new LlmUnavailableException("모의 장애");
			}
			embedded.addAll(inputs);
			// 첫 값(질의)과 답이 같은 방향이면 점수 1, lowSimilarity면 수직이라 0이다.
			List<float[]> vectors = new ArrayList<>();
			for (int index = 0; index < inputs.size(); index++) {
				vectors.add(lowSimilarity && index > 0 ? new float[] { 0f, 1f } : new float[] { 1f, 0f });
			}
			return vectors;
		}

		private String matcherOutput(Request request) {
			StringBuilder body = new StringBuilder("{");
			@SuppressWarnings("unchecked")
			Map<String, Object> properties = (Map<String, Object>) request.schema().get("properties");
			boolean first = true;
			for (String key : properties.keySet()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> versions = (Map<String, Object>) ((Map<String, Object>) properties.get(key)).get("properties");
				StringBuilder inner = new StringBuilder();
				for (String version : versions.keySet()) {
					inner.append(inner.length() == 0 ? "" : ",").append('"').append(version).append("\":\"").append(matcher.apply(key)).append('"');
				}
				body.append(first ? "" : ",").append('"').append(key).append("\":{").append(inner).append('}');
				first = false;
			}
			return body.append('}').toString();
		}

		private String classifierOutput(Request request) {
			StringBuilder labels = new StringBuilder();
			Matcher found = ANSWER.matcher(request.input());
			while (found.find()) {
				labels.append(labels.length() == 0 ? "" : ",")
					.append("{\"key\":\"").append(found.group(1)).append("\",\"label\":\"").append(classifier.apply(found.group(2))).append("\"}");
			}
			return "{\"labels\":[" + labels + "]}";
		}

		int calls(String purpose) {
			return (int) requests.stream().filter(request -> request.purpose().equals(purpose)).count();
		}

		int totalCalls() {
			return requests.size() + embedded.size();
		}

		String requestsText() {
			StringBuilder text = new StringBuilder();
			requests.forEach(request -> text.append(request.input()).append('\n'));
			embedded.forEach(input -> text.append(input).append('\n'));
			return text.toString();
		}

	}

}
