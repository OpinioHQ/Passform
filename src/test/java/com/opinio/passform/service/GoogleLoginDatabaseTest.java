package com.opinio.passform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.user.User;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;

// AC1을 실제 Postgres에서 시험한다. Google과의 통신만 모의 객체로 바꾼다.
class GoogleLoginDatabaseTest extends DatabaseTest {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	private static final OAuth2UserService<OidcUserRequest, OidcUser> MOCK_GOOGLE = call -> new DefaultOidcUser(
			List.of(new SimpleGrantedAuthority("OIDC_USER")), call.getIdToken());

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private AccountService accounts;

	@Autowired
	private UserRepository users;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private OidcUser googleLogin(String subject) {
		OidcIdToken token = new OidcIdToken("token", NOW, NOW.plusSeconds(60),
				Map.of("sub", subject, "email", "virtual@example.com", "name", "가상 사용자"));
		OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", NOW,
				NOW.plusSeconds(60));
		return new GoogleLoginService(MOCK_GOOGLE, accounts).loadUser(new OidcUserRequest(
				CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId("id").clientSecret("secret").build(), access,
				token));
	}

	@Test
	void AC1_모의_OAuth로_2회_로그인하면_User_1개이고_GET_me가_200이다() throws Exception {
		long before = users.count();
		String subject = UUID.randomUUID().toString();

		OidcUser first = googleLogin(subject);
		OidcUser second = googleLogin(subject);

		assertThat(users.count()).isEqualTo(before + 1);
		assertThat(second.getName()).isEqualTo(first.getName());
		mockMvc.perform(get("/me").with(oidcLogin().oidcUser(second)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user_id").value(second.getName()))
			.andExpect(jsonPath("$.email").value("virtual@example.com"))
			.andExpect(jsonPath("$.deadline_reminder").value(true));
	}

	@Test
	void 다른_Google_계정이면_User_2개다() {
		long before = users.count();

		OidcUser first = googleLogin(UUID.randomUUID().toString());
		OidcUser second = googleLogin(UUID.randomUUID().toString());

		assertThat(users.count()).isEqualTo(before + 2);
		assertThat(second.getName()).isNotEqualTo(first.getName());
	}

	@Test
	void 설정_변경이_DB에_남는다() throws Exception {
		OidcUser login = googleLogin(UUID.randomUUID().toString());

		mockMvc.perform(patch("/me/settings").with(oidcLogin().oidcUser(login))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"deadline_reminder\":false}")).andExpect(status().isOk());

		User saved = users.findById(UUID.fromString(login.getName())).orElseThrow();
		assertThat(saved.isDeadlineReminder()).isFalse();
	}

	@Test
	void 같은_계정의_동시_첫_로그인도_User_1개이고_모두_성공한다() throws Exception {
		int threads = 6;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			for (int round = 0; round < 5; round++) {
				long before = users.count();
				String subject = UUID.randomUUID().toString();
				CountDownLatch start = new CountDownLatch(1);
				List<Future<String>> logins = new ArrayList<>();
				for (int i = 0; i < threads; i++) {
					logins.add(pool.submit(() -> {
						assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
						return googleLogin(subject).getName();
					}));
				}
				start.countDown();
				HashSet<String> ids = new HashSet<>();
				for (Future<String> login : logins) {
					ids.add(login.get(30, TimeUnit.SECONDS));
				}
				assertThat(ids).hasSize(1);
				assertThat(users.count()).isEqualTo(before + 1);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

}
