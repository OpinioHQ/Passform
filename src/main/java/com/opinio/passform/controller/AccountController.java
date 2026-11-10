package com.opinio.passform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.annotation.JsonDeserialize;

import com.opinio.passform.service.AccountService;
import com.opinio.passform.service.AccountService.AccountView;
import com.opinio.passform.service.AccountService.SettingsCommand;
import com.opinio.passform.service.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
public class AccountController {

	private final AccountService accounts;

	private final CurrentUserService currentUser;

	public AccountController(AccountService accounts, CurrentUserService currentUser) {
		this.accounts = accounts;
		this.currentUser = currentUser;
	}

	@GetMapping("/me")
	public AccountView me() {
		return accounts.find(currentUser.requireUserId());
	}

	@PatchMapping("/me/settings")
	public void updateSettings(@Valid @RequestBody SettingsRequest request) {
		accounts.updateSettings(new SettingsCommand(currentUser.requireUserId(), request.deadlineReminder()));
	}

	// 세션을 끝내고 보안 문맥을 비운다(사람 결정, #57). 로그인과 CSRF 토큰이 있어야 부를 수 있다.
	@PostMapping("/me/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	// 생략하면 null이고 값을 유지한다. "true", 1 같은 boolean 아닌 값과 명시한 null은 읽기 단계에서 거부해 invalid_request다.
	public record SettingsRequest(@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean deadlineReminder) {
	}

}
