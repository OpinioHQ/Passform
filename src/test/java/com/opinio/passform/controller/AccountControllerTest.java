package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.opinio.passform.config.ClockConfig;
import com.opinio.passform.config.SecurityConfig;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.AccountService;
import com.opinio.passform.service.CurrentUserService;

// GET /me, PATCH /me/settings(SPEC 5절 99·101행). service는 실제 코드, 저장소만 모의 객체다.
@WebMvcTest(AccountController.class)
@Import({ SecurityConfig.class, ClockConfig.class, AccountService.class, CurrentUserService.class })
class AccountControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserRepository users;

	private User account;

	@BeforeEach
	void setUp() {
		account = new User("가상 사용자", "virtual@example.com", "google", "sub", Instant.EPOCH);
		org.springframework.test.util.ReflectionTestUtils.setField(account, "id", USER_ID);
		when(users.existsById(USER_ID)).thenReturn(true);
		when(users.findById(USER_ID)).thenReturn(Optional.of(account));
	}

	private ResultActions patchSettings(String body) throws Exception {
		return mockMvc.perform(patch("/me/settings").with(user(USER_ID.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	@Test
	void GET_me는_필드_4개를_돌려준다() throws Exception {
		mockMvc.perform(get("/me").with(user(USER_ID.toString())))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"user_id":"%s","name":"가상 사용자","email":"virtual@example.com","deadline_reminder":true}
					""".formatted(USER_ID), JsonCompareMode.STRICT));
	}

	@Test
	void GET_me는_비로그인이면_401이다() throws Exception {
		mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void 세션은_있으나_User_행이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/me").with(user(UUID.randomUUID().toString()))).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/me/settings").with(user(UUID.randomUUID().toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 사용자_ID가_아닌_인증_정보는_401이다() throws Exception {
		mockMvc.perform(get("/me").with(user("not-a-uuid"))).andExpect(status().isUnauthorized());
	}

	@Test
	void PATCH는_값을_바꾸고_본문_없는_200이다() throws Exception {
		patchSettings("{\"deadline_reminder\":false}").andExpect(status().isOk()).andExpect(content().string(""));
		assertThat(account.isDeadlineReminder()).isFalse();

		patchSettings("{\"deadline_reminder\":true}").andExpect(status().isOk());
		assertThat(account.isDeadlineReminder()).isTrue();
	}

	@Test
	void PATCH에서_키를_생략하면_값을_유지한다() throws Exception {
		account.changeDeadlineReminder(false);

		patchSettings("{}").andExpect(status().isOk());

		assertThat(account.isDeadlineReminder()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"true\"", "1", "null", "[]", "{}" })
	void PATCH에서_boolean이_아니면_invalid_request고_값을_바꾸지_않는다(String value) throws Exception {
		patchSettings("{\"deadline_reminder\":" + value + "}").andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"invalid_request\"}", JsonCompareMode.STRICT));
		assertThat(account.isDeadlineReminder()).isTrue();
	}

	@Test
	void PATCH는_비로그인이면_401이다() throws Exception {
		mockMvc.perform(patch("/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void PATCH에_CSRF_토큰이_없으면_403이다() throws Exception {
		mockMvc.perform(patch("/me/settings").with(user(USER_ID.toString()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"deadline_reminder\":false}")).andExpect(status().isForbidden());
		assertThat(account.isDeadlineReminder()).isTrue();
	}

}
