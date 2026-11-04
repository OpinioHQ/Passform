package com.opinio.passform.config;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 소셜 로그인 대신 고정 데모 계정으로 동작한다. 권한 판정(owner_id, 소유 관계)은 그대로 두고 신원만 바꾼다.
// X-Demo-User로 계정을 바꾸는 것은 비소유자 차단을 보여 주기 위한 것이다. demo 프로필 밖에서는 등록되지 않는다.
class DemoIdentityFilter extends OncePerRequestFilter {

	static final String DEFAULT_USER = "demo-user";

	static final String OTHER_USER = "other-user";

	private static final String HEADER = "X-Demo-User";

	private static final String ANONYMOUS = "anonymous";

	private static final Set<String> KNOWN_USERS = Set.of(DEFAULT_USER, OTHER_USER);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requested = request.getHeader(HEADER);
		if (!ANONYMOUS.equals(requested)) {
			String user = (requested != null && KNOWN_USERS.contains(requested)) ? requested : DEFAULT_USER;
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
						List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		}
		try {
			chain.doFilter(request, response);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

}
