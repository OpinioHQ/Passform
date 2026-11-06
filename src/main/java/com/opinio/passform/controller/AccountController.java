package com.opinio.passform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.annotation.JsonDeserialize;

import com.opinio.passform.service.AccountService;
import com.opinio.passform.service.AccountService.AccountView;
import com.opinio.passform.service.AccountService.SettingsCommand;
import com.opinio.passform.service.CurrentUserService;

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

	// 생략하면 null이고 값을 유지한다. "true", 1 같은 boolean 아닌 값과 명시한 null은 읽기 단계에서 거부해 invalid_request다.
	public record SettingsRequest(@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean deadlineReminder) {
	}

}
