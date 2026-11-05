package com.opinio.passform.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Google 로그인 결과를 users와 연결한다. 이 타입의 빈이 있으면 oauth2Login이 기본 구현 대신 쓴다.
@Service
public class GoogleLoginService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OAuth2UserService<OidcUserRequest, OidcUser> google;

	private final AccountService accounts;

	@Autowired
	public GoogleLoginService(AccountService accounts) {
		this(new OidcUserService(), accounts);
	}

	GoogleLoginService(OAuth2UserService<OidcUserRequest, OidcUser> google, AccountService accounts) {
		this.google = google;
		this.accounts = accounts;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest request) {
		OidcUser user = google.loadUser(request);
		String email = user.getEmail();
		// 이메일이 없으면 알림을 보낼 수 없어 로그인 실패다. 오류에 계정 값을 담지 않는다.
		if (!StringUtils.hasText(email)) {
			throw new OAuth2AuthenticationException(new OAuth2Error("missing_email"));
		}
		String name = StringUtils.hasText(user.getFullName()) ? user.getFullName() : email;
		String provider = request.getClientRegistration().getRegistrationId();
		return new LoginUser(accounts.findOrCreate(provider, user.getSubject(), email, name), user);
	}

}
