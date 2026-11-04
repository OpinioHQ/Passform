package com.opinio.passform.demo.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.demo.service.FormQueryService;
import com.opinio.passform.demo.service.SubmissionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

@RestController
public class FormController {

	private final FormQueryService forms;

	private final SubmissionService submissions;

	public FormController(FormQueryService forms, SubmissionService submissions) {
		this.forms = forms;
		this.submissions = submissions;
	}

	public record AnswerItem(@NotBlank String questionId, JsonNode value) {
	}

	public record SubmitRequest(@NotNull Integer formVersion, @NotNull List<@Valid AnswerItem> answers,
			Boolean consentAgreed, Boolean aiSummaryAgreed) {
	}

	@GetMapping("/f/{slug}")
	public FormQueryService.PublicFormView bySlug(@PathVariable String slug) {
		return forms.bySlug(slug);
	}

	@GetMapping("/forms/{formId}")
	public FormQueryService.PublicFormView byId(@PathVariable String formId) {
		return forms.byId(formId);
	}

	@PostMapping("/forms/{formId}/responses")
	@ResponseStatus(HttpStatus.CREATED)
	public SubmissionService.SubmittedView submit(@PathVariable String formId, @Valid @RequestBody SubmitRequest request,
			Authentication authentication) {
		return submissions.submit(formId, CurrentUser.idOrNull(authentication),
				new SubmissionService.SubmitCommand(request.formVersion(), toAnswers(request.answers()),
						Boolean.TRUE.equals(request.consentAgreed()), Boolean.TRUE.equals(request.aiSummaryAgreed())));
	}

	private static Map<String, List<String>> toAnswers(List<AnswerItem> items) {
		Map<String, List<String>> answers = new LinkedHashMap<>();
		for (AnswerItem item : items) {
			List<String> values = new ArrayList<>();
			if (item.value() != null && item.value().isArray()) {
				item.value().forEach(value -> values.add(value.asString()));
			}
			else if (item.value() != null && !item.value().isNull()) {
				values.add(item.value().asString());
			}
			answers.put(item.questionId(), values);
		}
		return answers;
	}

}
