package com.opinio.passform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;

// 쿠키·CORS·CSRF와 401. 값을 고정해 개발자의 .env가 결과를 바꾸지 못하게 한다.
// 제출 API 본체는 T3에 있다. 그 전까지 같은 경로의 테스트 전용 컨트롤러로 보안 설정만 시험한다.
@WebMvcTest(SecurityConfigTest.FakeSubmissionController.class)
@Import({ SecurityConfig.class, SecurityConfigTest.FakeSubmissionController.class })
@TestPropertySource(properties = { "SESSION_COOKIE_SECURE=true", "FRONTEND_ORIGIN=https://app.example.test",
		"CSRF_COOKIE_DOMAIN=.example.test", "GOOGLE_CLIENT_ID=test-client", "GOOGLE_CLIENT_SECRET=test-secret" })
class SecurityConfigTest {

	private static final String FRONTEND = "https://app.example.test";

	private static final String SUBMIT = "/forms/f1/responses";

	@RestController
	static class FakeSubmissionController {

		@PostMapping("/forms/{formId}/responses")
		@ResponseStatus(HttpStatus.CREATED)
		void submit(@PathVariable String formId) {
		}

	}

	@Autowired
	private MockMvc mockMvc;

	private String firstToken() throws Exception {
		return mockMvc.perform(get("/me")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
	}

	private ResultActions submitWith(String cookieToken, String headerToken) throws Exception {
		return mockMvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.cookie(new Cookie("XSRF-TOKEN", cookieToken))
			.header("X-XSRF-TOKEN", headerToken));
	}

	@Test
	void 비로그인_요청은_302가_아니라_401이다() throws Exception {
		mockMvc.perform(get("/me")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.LOCATION));
		mockMvc.perform(get("/me/forms")).andExpect(status().isUnauthorized());
	}

	@Test
	void 비로그인_브라우저도_첫_응답에서_읽을_수_있는_토큰_쿠키를_받는다() throws Exception {
		mockMvc.perform(get("/me"))
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
			.andExpect(cookie().secure("XSRF-TOKEN", true))
			.andExpect(cookie().sameSite("XSRF-TOKEN", "Lax"))
			.andExpect(cookie().domain("XSRF-TOKEN", ".example.test"));
	}

	@Test
	void 비로그인_제출은_쿠키의_토큰을_헤더로_보내면_통과한다() throws Exception {
		String token = firstToken();

		submitWith(token, token).andExpect(status().isCreated());
	}

	@Test
	void CSRF_토큰이_없거나_다르면_403이다() throws Exception {
		mockMvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
		submitWith(firstToken(), "wrong-token").andExpect(status().isForbidden());
	}

	@Test
	void 제출_경로만_열렸고_다른_POST는_로그인이_필요하다() throws Exception {
		String token = firstToken();
		mockMvc.perform(post("/forms").cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(SUBMIT + "/x").cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 허용_출처의_사전_요청에_자격_증명_허용_헤더가_있다() throws Exception {
		mockMvc.perform(options("/me/settings").header(HttpHeaders.ORIGIN, FRONTEND)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-xsrf-token"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void 다른_출처는_허용하지_않는다() throws Exception {
		mockMvc.perform(options("/me").header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		mockMvc.perform(get("/me").header(HttpHeaders.ORIGIN, "https://evil.example"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void 응답_401에도_허용_출처의_CORS_헤더가_있다() throws Exception {
		mockMvc.perform(get("/me").header(HttpHeaders.ORIGIN, FRONTEND))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void Google_로그인_시작은_openid_email_profile로_Google에_보낸다() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andReturn()
			.getResponse();

		assertThat(response.getRedirectedUrl()).startsWith("https://accounts.google.com/")
			.contains("scope=openid%20email%20profile")
			.contains("/login/oauth2/code/google");
	}

	@Test
	void 로그인에_실패하면_프런트_로그인_화면으로_보낸다() throws Exception {
		mockMvc.perform(get("/login/oauth2/code/google").param("code", "x").param("state", "y"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string(HttpHeaders.LOCATION, FRONTEND + "/login?error"));
	}

	@Test
	void 로그아웃_API는_없다() throws Exception {
		String token = firstToken();
		mockMvc.perform(post("/logout").cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token))
			.andExpect(status().isUnauthorized());
	}

}
