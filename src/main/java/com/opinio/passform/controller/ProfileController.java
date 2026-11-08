package com.opinio.passform.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.domain.form.ProfileAutofill;
import com.opinio.passform.service.AutofillService;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.ProfileService;
import com.opinio.passform.service.ProfileService.ProfileCommand;
import com.opinio.passform.service.ProfileService.ProfileView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 프로필과 자동채우기(SPEC 5절 102~106행). 모두 로그인 전용이다.
@RestController
public class ProfileController {

	private final ProfileService profiles;

	private final AutofillService autofill;

	private final CurrentUserService currentUser;

	public ProfileController(ProfileService profiles, AutofillService autofill, CurrentUserService currentUser) {
		this.profiles = profiles;
		this.autofill = autofill;
		this.currentUser = currentUser;
	}

	@GetMapping("/me/profiles")
	public List<ProfileView> list() {
		return profiles.list(currentUser.requireUserId());
	}

	@PostMapping("/me/profiles")
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedProfile create(@RequestBody ProfileRequest request) {
		return new CreatedProfile(profiles.create(command(request)));
	}

	@PutMapping("/me/profiles/{profileId}")
	public void update(@PathVariable String profileId, @RequestBody ProfileRequest request) {
		profiles.update(profileId, command(request));
	}

	@DeleteMapping("/me/profiles/{profileId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String profileId) {
		profiles.delete(currentUser.requireUserId(), profileId);
	}

	@PostMapping("/forms/{formId}/autofill")
	public ProfileAutofill.Result autofill(@PathVariable String formId, @Valid @RequestBody AutofillRequest request) {
		return autofill.autofill(currentUser.requireUserId(), formId, request.profileId());
	}

	private ProfileCommand command(ProfileRequest request) {
		return new ProfileCommand(currentUser.requireUserId(), request.label(), request.personalInfo());
	}

	// 허용 키 검사를 형식 검사보다 먼저 하려고 도메인에서 판정한다(AC3). label은 숫자가 문자열로 바뀌지 않게 Object로 받는다.
	public record ProfileRequest(Object label, Map<String, Object> personalInfo) {
	}

	public record CreatedProfile(UUID profileId) {
	}

	public record AutofillRequest(@NotNull String profileId) {
	}

}
