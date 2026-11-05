package com.opinio.passform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.service.AccountService;
import com.opinio.passform.service.AccountService.AccountView;
import com.opinio.passform.service.CurrentUserService;

@RestController
public class AccountController {

	private static final String DEADLINE_REMINDER = "deadline_reminder";

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

	// Boolean 필드로 받으면 Jackson이 "true"와 1을 true로 바꾸고 null과 생략을 구분하지 못해 본문을 그대로 읽는다.
	@PatchMapping("/me/settings")
	public void updateSettings(@RequestBody Map<String, Object> body) {
		Object value = body.get(DEADLINE_REMINDER);
		if (body.containsKey(DEADLINE_REMINDER) && !(value instanceof Boolean)) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		accounts.updateSettings(currentUser.requireUserId(), (Boolean) value);
	}

}
