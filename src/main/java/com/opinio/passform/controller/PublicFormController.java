package com.opinio.passform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.PublicFormService;
import com.opinio.passform.service.PublicFormService.PublicFormView;

// 폼 공개 조회(SPEC 5절 187행). 로그인이 필요 없다.
@RestController
public class PublicFormController {

	private final PublicFormService publicForms;

	public PublicFormController(PublicFormService publicForms) {
		this.publicForms = publicForms;
	}

	@GetMapping("/f/{slug}")
	public PublicFormView bySlug(@PathVariable String slug) {
		return publicForms.findBySlug(slug);
	}

	@GetMapping("/forms/{formId}")
	public PublicFormView byId(@PathVariable String formId) {
		return publicForms.findById(formId);
	}

}
