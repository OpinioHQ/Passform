package com.opinio.passform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.GET, "/health").permitAll()
				// 비로그인 응답 제출(SPEC 5절 188행). CSRF는 그대로 켠다.
				.requestMatchers(HttpMethod.POST, "/forms/*/responses").permitAll()
				.anyRequest().authenticated())
			// oauth2Login을 켜기 전까지 비인증 요청의 응답을 401로 고정한다.
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.build();
	}

}
