package com.opinio.passform.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.QuestionTemplateService;
import com.opinio.passform.service.QuestionTemplateService.QuestionTemplateView;

@RestController
public class QuestionTemplateController {

	private final QuestionTemplateService questionTemplates;

	public QuestionTemplateController(QuestionTemplateService questionTemplates) {
		this.questionTemplates = questionTemplates;
	}

	// 로그인이 필요하다(SPEC 5절 113행). SecurityConfig의 기본 규칙이 맡는다.
	// 빠진 값도 목록 밖 값과 같이 서비스에서 400 invalid_request로 처리한다.
	@GetMapping("/question-templates")
	public List<QuestionTemplateView> list(@RequestParam(required = false) String purpose,
			@RequestParam(required = false) String audience) {
		return questionTemplates.list(purpose, audience);
	}

}
