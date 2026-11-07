package com.opinio.passform.controller;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.formatted;
import static com.opinio.passform.support.FormDefinitions.limited;
import static com.opinio.passform.support.FormDefinitions.question;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.opinio.passform.config.JacksonConfig;
import com.opinio.passform.config.SecurityConfig;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.SubmissionService;

// 비로그인 제출과 오류별 HTTP 상태. service는 실제 코드, 저장소만 모의 객체다.
@WebMvcTest(SubmissionController.class)
@Import({ SecurityConfig.class, JacksonConfig.class, SubmissionService.class, CurrentUserService.class,
		SubmissionControllerTest.FixedClock.class })
class SubmissionControllerTest {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	private static final UUID FORM_ID = UUID.randomUUID();

	private static final UUID RESPONSE_ID = UUID.randomUUID();

	private static final String URL = "/forms/" + FORM_ID + "/responses";

	private static final String Q1 = "{\"question_id\":\"q1\",\"value\":\"값\"}";

	private static final Map<String, Object> DEFINITION = definition("one_question", List.of(section("s1", text("q1", true),
			question("q2", "dropdown", false, List.of("가", "나")), question("q3", "checkbox", false, List.of("다", "라")),
			formatted("q4", false, "email"), limited("q5", "long_answer", 3))));

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FormRepository forms;

	@MockitoBean
	private FormVersionRepository versions;

	@MockitoBean
	private ResponseRepository responses;

	@MockitoBean
	private UserRepository users;

	private Form form;

	@BeforeEach
	void setUp() {
		form = new Form(UUID.randomUUID(), DEFINITION, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "status", FormStatus.OPEN);
		ReflectionTestUtils.setField(form, "definitionVersion", 2);
		when(forms.findByIdForUpdate(FORM_ID)).thenReturn(Optional.of(form));
		when(versions.findById(new FormVersionId(FORM_ID, 2)))
			.thenReturn(Optional.of(new FormVersion(FORM_ID, 2, DEFINITION, Instant.EPOCH)));
		when(responses.save(any(Response.class))).thenAnswer(call -> {
			Response response = call.getArgument(0);
			ReflectionTestUtils.setField(response, "id", RESPONSE_ID);
			return response;
		});
	}

	private static String body(String answers, String extra) {
		return "{\"form_version\":2,\"answers\":" + answers + extra + "}";
	}

	private static String consented(String answers) {
		return body(answers, ",\"consent_agreed\":true");
	}

	private static String withQ1(String questionId, String value) {
		return consented("[" + Q1 + ",{\"question_id\":\"" + questionId + "\",\"value\":" + value + "}]");
	}

	private static MockHttpServletRequestBuilder request(String url, String body) {
		return post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private ResultActions send(String body) throws Exception {
		return mockMvc.perform(request(URL, body));
	}

	private void assertError(ResultActions result, int status, String json) throws Exception {
		result.andExpect(status().is(status)).andExpect(content().json(json, JsonCompareMode.STRICT));
		verify(responses, never()).save(any());
	}

	@Test
	void 비로그인_제출은_201과_SPEC_형태의_본문을_돌려준다() throws Exception {
		send(withQ1("q3", "[\"다\"]")).andExpect(status().isCreated())
			.andExpect(content().json("""
					{"response_id":"%s","form_version":2,"status":"submitted",
					 "content":[{"question_id":"q1","question_note":"q1 문구","value":"값"},
					            {"question_id":"q3","question_note":"q3 문구","value":["다"]}],
					 "attachments":[],"submitted_at":"2026-11-01T00:00:00Z","updated_at":null}
					""".formatted(RESPONSE_ID), JsonCompareMode.STRICT));

		ArgumentCaptor<Response> saved = ArgumentCaptor.forClass(Response.class);
		verify(responses).save(saved.capture());
		assertThat(saved.getValue().getUserId()).isNull();
		assertThat(saved.getValue().isAiSummaryAgreed()).isFalse();
	}

	@Test
	void 로그인_제출은_그_사용자의_응답으로_저장한다() throws Exception {
		UUID userId = UUID.randomUUID();

		when(users.existsById(userId)).thenReturn(true);

		mockMvc.perform(request(URL, consented("[" + Q1 + "]")).with(user(userId.toString())))
			.andExpect(status().isCreated());

		ArgumentCaptor<Response> saved = ArgumentCaptor.forClass(Response.class);
		verify(responses).save(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(userId);
	}

	@Test
	void 세션은_있으나_User_행이_없으면_401이고_저장하지_않는다() throws Exception {
		mockMvc.perform(request(URL, consented("[" + Q1 + "]")).with(user(UUID.randomUUID().toString())))
			.andExpect(status().isUnauthorized());
		verify(responses, never()).save(any());
	}

	// 리뷰: 동의는 JSON boolean, 버전은 정수만 받는다. 잘못된 타입은 invalid_request다.
	@ParameterizedTest
	@ValueSource(strings = { "\"consent_agreed\":\"true\"", "\"consent_agreed\":2", "\"consent_agreed\":1",
			"\"consent_agreed\":true,\"ai_summary_agreed\":1", "\"consent_agreed\":true,\"ai_summary_agreed\":\"true\"" })
	void 동의_값이_boolean이_아니면_invalid_request다(String consent) throws Exception {
		assertError(send("{\"form_version\":2,\"answers\":[" + Q1 + "]," + consent + "}"), 400,
				"{\"error\":\"invalid_request\"}");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2.9", "2.0", "\"2\"", "true" })
	void 버전이_정수가_아니면_invalid_request다(String version) throws Exception {
		assertError(send("{\"form_version\":" + version + ",\"answers\":[" + Q1 + "],\"consent_agreed\":true}"), 400,
				"{\"error\":\"invalid_request\"}");
	}

	@Test
	void AC14_동의를_생략하면_invalid_request가_아니라_consent_required다() throws Exception {
		assertError(send(body("[" + Q1 + "]", "")), 400, "{\"error\":\"consent_required\"}");
	}

	@Test
	void AC14_동의가_false면_consent_required다() throws Exception {
		assertError(send(body("[" + Q1 + "]", ",\"consent_agreed\":false")), 400, "{\"error\":\"consent_required\"}");
	}

	@Test
	void 필수_문항을_비우면_400과_문항_id다() throws Exception {
		assertError(send(consented("[]")), 400, "{\"error\":\"required_missing\",\"question_ids\":[\"q1\"]}");
	}

	@Test
	void 선택지_밖_값은_400이다() throws Exception {
		assertError(send(withQ1("q2", "\"없음\"")), 400, "{\"error\":\"invalid_option\",\"question_ids\":[\"q2\"]}");
	}

	@Test
	void 형식에_맞지_않으면_400이다() throws Exception {
		assertError(send(withQ1("q4", "\"a@@b.c\"")), 400,
				"{\"error\":\"invalid_input_format_value\",\"question_ids\":[\"q4\"]}");
	}

	@Test
	void 글자_수를_넘으면_400이다() throws Exception {
		assertError(send(withQ1("q5", "\"네글자임\"")), 400,
				"{\"error\":\"character_limit_exceeded\",\"question_ids\":[\"q5\"]}");
	}

	@Test
	void 값_모양이_다르면_invalid_request와_문항_id다() throws Exception {
		assertError(send(withQ1("q3", "\"다\"")), 400, "{\"error\":\"invalid_request\",\"question_ids\":[\"q3\"]}");
	}

	@Test
	void 닫힌_폼은_409다() throws Exception {
		ReflectionTestUtils.setField(form, "status", FormStatus.CLOSED);
		assertError(send(consented("[" + Q1 + "]")), 409, "{\"error\":\"form_closed\"}");
	}

	@Test
	void 옛_버전은_409다() throws Exception {
		assertError(send("{\"form_version\":1,\"answers\":[],\"consent_agreed\":true}"), 409,
				"{\"error\":\"form_version_changed\"}");
	}

	@Test
	void 없는_폼과_UUID가_아닌_ID는_404다() throws Exception {
		assertError(mockMvc.perform(request("/forms/" + UUID.randomUUID() + "/responses", consented("[]"))), 404,
				"{\"error\":\"not_found\"}");
		assertError(mockMvc.perform(request("/forms/not-uuid/responses", consented("[]"))), 404,
				"{\"error\":\"not_found\"}");
	}

	@Test
	void form_version이나_answers가_없으면_invalid_request다() throws Exception {
		assertError(send("{\"answers\":[],\"consent_agreed\":true}"), 400, "{\"error\":\"invalid_request\"}");
		assertError(send("{\"form_version\":2,\"consent_agreed\":true}"), 400, "{\"error\":\"invalid_request\"}");
	}

	@Test
	void CSRF_토큰이_없으면_거부한다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(consented("[" + Q1 + "]")))
			.andExpect(status().isForbidden());
		verify(responses, never()).save(any());
	}

	@Test
	void 이_경로_외에는_여전히_로그인이_필요하다() throws Exception {
		mockMvc.perform(request("/forms", "{}")).andExpect(status().isUnauthorized());
		mockMvc.perform(request("/forms/" + FORM_ID, "{}")).andExpect(status().isUnauthorized());
		mockMvc.perform(request("/forms/" + FORM_ID + "/responses/x", "{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 오류_응답에_답_값이_없다() throws Exception {
		String json = send(withQ1("q4", "\"virtual@@b.c\""))
			.andExpect(jsonPath("$.error").value("invalid_input_format_value"))
			.andReturn().getResponse().getContentAsString();
		assertThat(json).doesNotContain("virtual");
	}

}
