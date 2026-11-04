package com.opinio.passform.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.demo.service.MeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/me")
public class MeController {

	private final MeService me;

	public MeController(MeService me) {
		this.me = me;
	}

	public record SaveFormRequest(@NotBlank String formId) {
	}

	public record SettingsRequest(String uiMode) {
	}

	@GetMapping
	public MeService.UserView me(Authentication authentication) {
		return me.me(authentication.getName());
	}

	@PatchMapping("/settings")
	public MeService.UserView settings(@RequestBody SettingsRequest request, Authentication authentication) {
		return me.changeUiMode(authentication.getName(), request.uiMode());
	}

	@GetMapping("/forms")
	public List<MeService.SavedFormView> forms(Authentication authentication) {
		return me.myForms(authentication.getName());
	}

	@PostMapping("/saved-forms")
	public ResponseEntity<Void> saveForm(@Valid @RequestBody SaveFormRequest request, Authentication authentication) {
		boolean created = me.saveForm(authentication.getName(), request.formId());
		return ResponseEntity.status(created ? 201 : 200).build();
	}

	@GetMapping("/created-forms")
	public List<MeService.CreatedFormView> createdForms(Authentication authentication) {
		return me.createdForms(authentication.getName());
	}

	@GetMapping("/responses")
	public List<MeService.HistoryItem> history(Authentication authentication) {
		return me.history(authentication.getName());
	}

	@GetMapping("/responses/{responseId}")
	public MeService.ResponseDetail historyDetail(@PathVariable String responseId, Authentication authentication) {
		return me.historyDetail(authentication.getName(), responseId);
	}

}
