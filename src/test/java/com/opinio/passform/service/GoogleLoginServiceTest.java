package com.opinio.passform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.user.User;
import com.opinio.passform.repository.UserRepository;

class GoogleLoginServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	private final UserRepository users = mock(UserRepository.class);

	private OidcUserRequest request(Map<String, Object> claims) {
		ClientRegistration registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
			.clientId("id")
			.clientSecret("secret")
			.build();
		OidcIdToken token = new OidcIdToken("token", NOW, NOW.plusSeconds(60), claims);
		OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", NOW,
				NOW.plusSeconds(60));
		return new OidcUserRequest(registration, access, token);
	}

	private OidcUser login(String email, String name) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", "google-sub");
		if (email != null) {
			claims.put("email", email);
		}
		if (name != null) {
			claims.put("name", name);
		}
		OAuth2UserService<OidcUserRequest, OidcUser> google = call -> new DefaultOidcUser(
				List.of(new SimpleGrantedAuthority("OIDC_USER")), call.getIdToken());
		AccountService accounts = new AccountService(users, Clock.fixed(NOW, ZoneOffset.UTC));
		return new GoogleLoginService(google, accounts).loadUser(request(claims));
	}

	private void givenNewUserIsSaved() {
		when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
			User user = call.getArgument(0);
			ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
			return user;
		});
	}

	private User savedUser() {
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		return saved.getValue();
	}

	@Test
	void AC1_처음_로그인하면_User를_만들고_이름은_User_ID다() {
		givenNewUserIsSaved();

		OidcUser principal = login("virtual@example.com", "가상 사용자");

		User user = savedUser();
		assertThat(user.getEmail()).isEqualTo("virtual@example.com");
		assertThat(user.getName()).isEqualTo("가상 사용자");
		assertThat(user.getOauthProvider()).isEqualTo("google");
		assertThat(user.getOauthSubject()).isEqualTo("google-sub");
		assertThat(user.isDeadlineReminder()).isTrue();
		assertThat(user.getCreatedAt()).isEqualTo(NOW);
		assertThat(principal.getName()).isEqualTo(user.getId().toString());
	}

	@Test
	void 재로그인은_새_User를_만들지_않고_이름과_이메일을_갱신하지_않는다() {
		User existing = new User("예전 이름", "old@example.com", "google", "google-sub", NOW);
		ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
		when(users.findByOauthProviderAndOauthSubject("google", "google-sub")).thenReturn(Optional.of(existing));

		OidcUser principal = login("new@example.com", "새 이름");

		assertThat(principal.getName()).isEqualTo(existing.getId().toString());
		assertThat(existing.getName()).isEqualTo("예전 이름");
		assertThat(existing.getEmail()).isEqualTo("old@example.com");
		verify(users, never()).saveAndFlush(any());
		verify(users, never()).save(any());
	}

	@Test
	void email이_없으면_로그인_실패고_User를_만들지_않는다() {
		assertThatThrownBy(() -> login(null, "가상 사용자")).isInstanceOf(OAuth2AuthenticationException.class);
		assertThatThrownBy(() -> login(" ", "가상 사용자")).isInstanceOf(OAuth2AuthenticationException.class);
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void name이_없으면_email을_name으로_저장한다() {
		givenNewUserIsSaved();

		login("virtual@example.com", null);

		assertThat(savedUser().getName()).isEqualTo("virtual@example.com");
	}

	@Test
	void 동시_첫_로그인으로_유일_제약을_위반하면_기존_User로_로그인한다() {
		User winner = new User("가상", "virtual@example.com", "google", "google-sub", NOW);
		ReflectionTestUtils.setField(winner, "id", UUID.randomUUID());
		when(users.findByOauthProviderAndOauthSubject("google", "google-sub")).thenReturn(Optional.empty(),
				Optional.of(winner));
		when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_oauth"));

		assertThat(login("virtual@example.com", "가상").getName()).isEqualTo(winner.getId().toString());
	}

	@Test
	void 로그인_정보의_문자열에_이메일과_이름이_없다() {
		givenNewUserIsSaved();

		assertThat(login("virtual@example.com", "가상 사용자").toString()).doesNotContain("virtual", "가상");
	}

}
