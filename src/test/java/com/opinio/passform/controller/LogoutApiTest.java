package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

// 로그아웃(사람 결정, #57): 로그인 사용자만 POST /me/logout으로 세션을 끝낸다. 기본 /logout은 열지 않는다.
class LogoutApiTest extends DatabaseTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void 로그인_사용자가_부르면_204이고_세션이_끝난다() throws Exception {
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/me/logout").session(session).with(user(UUID.randomUUID().toString())).with(csrf()))
			.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void 로그아웃한_세션으로는_me가_401이다() throws Exception {
		UUID userId = users.saveAndFlush(TestData.newUser()).getId();
		MockHttpSession session = new MockHttpSession();
		// 실제 로그인처럼 세션에 보안 문맥을 저장한다. 이후 요청은 세션만으로 로그인 상태가 된다.
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(
				UsernamePasswordAuthenticationToken.authenticated(userId.toString(), null, List.of())));
		mockMvc.perform(get("/me").session(session)).andExpect(status().isOk());

		mockMvc.perform(post("/me/logout").session(session).with(csrf())).andExpect(status().isNoContent());

		mockMvc.perform(get("/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void 로그인이_없으면_401이다() throws Exception {
		mockMvc.perform(post("/me/logout").with(csrf())).andExpect(status().isUnauthorized());
	}

	@Test
	void CSRF_토큰이_없으면_403이고_세션은_그대로다() throws Exception {
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/me/logout").session(session).with(user(UUID.randomUUID().toString())))
			.andExpect(status().isForbidden());

		assertThat(session.isInvalid()).isFalse();
	}

}
