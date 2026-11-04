package com.opinio.passform.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

// 노트북 시연 전용. SecurityConfig를 고치지 않고 API 경로에만 앞서 적용한다. application-demo.properties가 127.0.0.1에만 묶는다.
@Configuration
@Profile("demo")
public class DemoSecurityConfig {

	@Bean
	@Order(0)
	public SecurityFilterChain demoFilterChain(HttpSecurity http) throws Exception {
		return http.securityMatcher("/forms/**", "/f/**", "/me/**", "/error")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new DemoIdentityFilter(), AnonymousAuthenticationFilter.class)
			.authorizeHttpRequests(requests -> requests.requestMatchers("/error")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/f/*", "/forms/*")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/forms/*/responses")
				.permitAll()
				.anyRequest()
				.authenticated())
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.build();
	}

}
