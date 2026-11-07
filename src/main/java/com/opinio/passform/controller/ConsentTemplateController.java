package com.opinio.passform.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.ConsentTemplateService;
import com.opinio.passform.service.ConsentTemplateService.ConsentTemplateView;

@RestController
public class ConsentTemplateController {

	private final ConsentTemplateService consentTemplates;

	public ConsentTemplateController(ConsentTemplateService consentTemplates) {
		this.consentTemplates = consentTemplates;
	}

	// 로그인이 필요하다(SPEC 5절 114행). SecurityConfig의 기본 규칙이 맡는다.
	@GetMapping("/consent-templates")
	public List<ConsentTemplateView> list() {
		return consentTemplates.list();
	}

}
