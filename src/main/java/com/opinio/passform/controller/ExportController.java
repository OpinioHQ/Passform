package com.opinio.passform.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.ExportService;
import com.opinio.passform.service.ExportService.ExportCommand;
import com.opinio.passform.service.ExportService.ExportOutcome;
import com.opinio.passform.service.analysis.AnalyzeView.ClarifyView;

// 제작자 전용(SPEC 239행). 파일 이름은 화면이 정한다. SecurityConfig가 Content-Disposition을 노출하지 않기 때문이다.
@RestController
public class ExportController {

	private final ExportService exports;

	private final CurrentUserService currentUser;

	public ExportController(ExportService exports, CurrentUserService currentUser) {
		this.exports = exports;
		this.currentUser = currentUser;
	}

	// 본문을 문자열로 받아 service에서 판정한다. DTO로 받으면 소유 확인보다 형식 오류가 먼저 나간다(AC2).
	@PostMapping("/forms/{formId}/export")
	public ResponseEntity<?> export(@PathVariable String formId, @RequestBody(required = false) String body) {
		ExportOutcome outcome = exports.export(new ExportCommand(currentUser.requireUserId(), formId, body));
		if (outcome.clarify() != null) {
			return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new ClarifyBody("clarify", outcome.clarify()));
		}
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(outcome.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
			.body(outcome.content());
	}

	public record ClarifyBody(String status, ClarifyView clarify) {
	}

}
