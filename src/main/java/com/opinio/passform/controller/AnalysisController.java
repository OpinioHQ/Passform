package com.opinio.passform.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.analysis.AnalysisService;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.AnalyzeView;

@RestController
public class AnalysisController {

	private final AnalysisService analysis;

	private final CurrentUserService currentUser;

	public AnalysisController(AnalysisService analysis, CurrentUserService currentUser) {
		this.analysis = analysis;
		this.currentUser = currentUser;
	}

	// 본문과 form_id를 문자열로 받아 service에서 판정한다. DTO로 받으면 소유 확인보다 형식 오류가 먼저 나간다(AC2).
	@PostMapping("/forms/{formId}/analyze")
	public AnalyzeView analyze(@PathVariable String formId, @RequestBody(required = false) String body) {
		return analysis.analyze(new AnalyzeCommand(currentUser.requireUserId(), formId, body));
	}

}
