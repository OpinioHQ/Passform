package com.opinio.passform.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.RequestContextSchema;
import com.opinio.passform.service.analysis.SummaryService;

// 규칙으로 못 푼 조건의 LLM 매칭. 실제 호출 없이 LlmClient를 모의 객체로 바꿔 요청 본문과 출력 처리를 본다.
class LlmConditionMatcherTest {

	private static final Map<String, Object> DEFINITION = Map.of("sections", List.of(Map.of("section_id", "s1", "questions",
			List.of(Map.of("question_id", "q1", "question_note", "학과", "question_type", "dropdown", "options",
					List.of("소프트웨어학과", "경영학과")),
					Map.of("question_id", "q2", "question_note", "연락처", "question_type", "short_answer", "options", List.of(),
							"input_format", Map.of("kind", "mobile_phone")),
					Map.of("question_id", "q3", "question_note", "자기소개", "question_type", "long_answer", "options",
							List.of())))));

	private static final VersionQuestions V1 = VersionQuestions.of(1, DEFINITION);

	private final LlmClient client = mock(LlmClient.class);

	private final RuleFirstConditionMatcher matcher = new RuleFirstConditionMatcher(new LlmConditionMatcher(client));

	private LlmRequest onlyRequest() {
		ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
		verify(client, times(1)).respond(request.capture());
		return request.getValue();
	}

	@Test
	void 허용_밖_값과_NONE은_버린다() {
		when(client.respond(any())).thenReturn("{\"c0\":{\"v1\":\"q1|option|소프트웨어학과\"},\"c1\":{\"v1\":\"q1|option|수의학과\"},"
				+ "\"c2\":{\"v1\":\"q9|answered\"},\"c3\":{\"v1\":\"NONE\"}}");

		List<ConditionMatch> matches = matcher.match(List.of("소웨", "수의대", "없는 문항", "인문대"), List.of(V1));

		assertThat(matches).containsExactly(new ConditionMatch("소웨", 1, "q1", MatchKind.OPTION, "소프트웨어학과"));
	}

	@Test
	void 규칙으로_푼_조건은_보내지_않는다() {
		when(client.respond(any())).thenReturn("{\"c0\":{\"v1\":\"NONE\"}}");

		matcher.match(List.of("경영학과", "소웨"), List.of(V1));

		LlmRequest request = onlyRequest();
		assertThat(request.input()).contains("<conditions>\n{\"c0\":\"소웨\"}\n</conditions>");
		assertThat(request.instructions()).doesNotContain("{numbering_rule}").contains(LlmConditionMatcher.NUMBERING_RULE);
	}

	@Test
	void 응답_값은_요청_본문에_없다() {
		UUID owner = UUID.randomUUID();
		UUID formId = UUID.randomUUID();
		Response response = new Response(formId, owner, 1, ResponseStatus.SUBMITTED, List.of(new Answer("q1", "경영학과"),
				new Answer("q2", "010-9876-5432"), new Answer("q3", "비밀스러운 자기소개 문장")), true, null, Instant.EPOCH);
		ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
		Form form = new Form(owner, DEFINITION, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", formId);
		ReflectionTestUtils.setField(form, "definitionVersion", 1);
		FormRepository forms = mock(FormRepository.class);
		when(forms.findById(formId)).thenReturn(Optional.of(form));
		FormVersionRepository versions = mock(FormVersionRepository.class);
		when(versions.findById(new FormVersionId(formId, 1)))
			.thenReturn(Optional.of(new FormVersion(formId, 1, DEFINITION, Instant.EPOCH)));
		ResponseRepository responses = mock(ResponseRepository.class);
		when(responses.findSubmittedNotDeletedByFormId(formId)).thenReturn(List.of(response));
		when(client.respond(any())).thenReturn("{\"c0\":{\"v1\":\"q1|option|경영학과\"}}");
		AnalysisService service = new AnalysisService(forms, versions, responses, mock(QueryParser.class), matcher,
				new RequestContextSchema(), mock(SummaryService.class));

		service.analyze(new AnalyzeCommand(owner, formId.toString(),
				"{\"context\":{\"conditions\":[\"경영\"],\"operation\":\"count\"}}"));

		LlmRequest request = onlyRequest();
		assertThat(request.input() + request.instructions()).doesNotContain("010-9876-5432", "비밀스러운 자기소개 문장");
	}

	@Test
	void 키가_없으면_파서처럼_503이다() {
		RuleFirstConditionMatcher keyless = new RuleFirstConditionMatcher(
				new LlmConditionMatcher(new OpenAiClient(null, "http://127.0.0.1:9")));

		assertThatThrownBy(() -> keyless.match(List.of("소웨"), List.of(V1))).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNAVAILABLE);
	}

}
