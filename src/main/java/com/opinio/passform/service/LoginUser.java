package com.opinio.passform.service;

import java.util.UUID;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

// 세션에 남는 로그인 정보. 인증 정보의 이름은 Google 고유 ID가 아니라 users의 ID다.
public class LoginUser extends DefaultOidcUser {

	private final UUID userId;

	LoginUser(UUID userId, OidcUser google) {
		super(google.getAuthorities(), google.getIdToken(), google.getUserInfo());
		this.userId = userId;
	}

	public UUID getUserId() {
		return userId;
	}

	@Override
	public String getName() {
		return userId.toString();
	}

	// 기본 구현은 이메일과 이름을 그대로 출력한다. 로그에 남지 않게 ID만 보여 준다.
	@Override
	public String toString() {
		return "LoginUser[" + userId + "]";
	}

}
