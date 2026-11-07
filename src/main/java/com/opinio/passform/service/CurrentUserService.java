package com.opinio.passform.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.opinio.passform.repository.UserRepository;

// 현재 사용자를 얻는 한 곳이다. 세션은 있는데 User 행이 없으면 401이다.
// 이 예외는 advice를 지나 보안 필터의 401로 나간다.
@Service
public class CurrentUserService {

	private final UserRepository users;

	public CurrentUserService(UserRepository users) {
		this.users = users;
	}

	// 공개 API용. 비로그인이면 빈 값이다.
	public Optional<UUID> findUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		UUID userId = parse(authentication.getName());
		if (userId == null || !users.existsById(userId)) {
			throw noLogin();
		}
		return Optional.of(userId);
	}

	// 로그인이 필요한 API용.
	public UUID requireUserId() {
		return findUserId().orElseThrow(CurrentUserService::noLogin);
	}

	private static UUID parse(String name) {
		try {
			return UUID.fromString(name);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	static AuthenticationCredentialsNotFoundException noLogin() {
		return new AuthenticationCredentialsNotFoundException("로그인 필요");
	}

}
