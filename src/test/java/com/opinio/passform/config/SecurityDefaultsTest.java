package com.opinio.passform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.server.Cookie;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

// application.properties의 기본값을 환경변수와 .env 없이 읽는다. 개발자의 .env가 결과를 바꾸지 못한다.
class SecurityDefaultsTest {

	private final MockEnvironment defaults = new MockEnvironment();

	@BeforeEach
	void setUp() throws IOException {
		defaults.getPropertySources()
			.addFirst(new PropertiesPropertySource("application",
					PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))));
	}

	@Test
	void 세션_쿠키의_기본값은_Secure와_SameSite_Lax다() {
		Cookie cookie = Binder.get(defaults).bind("server.servlet.session.cookie", Cookie.class).get();

		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
	}

	@Test
	void Google_권한은_openid_email_profile뿐이다() {
		List<String> scope = Binder.get(defaults)
			.bind("spring.security.oauth2.client.registration.google.scope", List.class)
			.map(values -> values.stream().map(String::valueOf).toList())
			.get();

		assertThat(scope).containsExactly("openid", "email", "profile");
	}

	@Test
	void 로컬_기본_출처는_5173이다() {
		assertThat(defaults.resolvePlaceholders("${passform.frontend-origin}")).isEqualTo("http://localhost:5173");
	}

}
