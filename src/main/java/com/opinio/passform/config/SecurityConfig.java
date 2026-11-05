package com.opinio.passform.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${passform.frontend-origin}") String frontendOrigin,
			@Value("${server.servlet.session.cookie.secure}") boolean secureCookies,
			@Value("${passform.csrf-cookie-domain}") String csrfCookieDomain) throws Exception {
		return http
			.cors(Customizer.withDefaults())
			// 화면이 쿠키의 토큰을 X-XSRF-TOKEN 헤더로 보낸다. 모든 응답이 토큰 쿠키를 주므로 비로그인 브라우저도 첫 응답에서 받는다.
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens(secureCookies, csrfCookieDomain)))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.GET, "/health").permitAll()
				// 비로그인 응답 제출(SPEC 5절 188행). CSRF는 그대로 켠다.
				.requestMatchers(HttpMethod.POST, "/forms/*/responses").permitAll()
				.anyRequest().authenticated())
			// 로그인 뒤에는 항상 설정한 프런트로 보낸다. 요청 파라미터의 주소로 보내지 않는다.
			.oauth2Login(login -> login
				.loginPage(frontendOrigin + "/login")
				.defaultSuccessUrl(frontendOrigin, true)
				.failureUrl(frontendOrigin + "/login?error"))
			// SPEC에 로그아웃 API가 없다.
			.logout(AbstractHttpConfigurer::disable)
			// 비로그인 요청의 응답을 401로 고정한다. oauth2Login을 켜도 로그인 페이지로 302 하지 않는다.
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
				// 기본 처리는 sendError라 실제 서버에서 /error로 다시 전달되고, 비로그인이면 그 경로가 막혀 CSRF 거부가 401로 바뀐다.
				.accessDeniedHandler((request, response, denied) -> response.setStatus(HttpStatus.FORBIDDEN.value())))
			.build();
	}

	// app.<도메인>의 화면이 api.<도메인>이 준 토큰을 읽도록 쿠키 도메인을 환경변수로 받는다. 로컬에서는 비워 둔다.
	private static CookieCsrfTokenRepository csrfTokens(boolean secure, String domain) {
		CookieCsrfTokenRepository tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
		tokens.setCookieCustomizer(cookie -> {
			cookie.sameSite("Lax").secure(secure);
			if (StringUtils.hasText(domain)) {
				cookie.domain(domain);
			}
		});
		return tokens;
	}

	// 설정한 프런트 출처 하나만 허용한다(ARCHITECTURE 10절).
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${passform.frontend-origin}") String frontendOrigin) {
		Assert.isTrue(!frontendOrigin.contains("*"), "프런트 출처에 와일드카드를 쓸 수 없다");
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(frontendOrigin));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
		cors.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

}
