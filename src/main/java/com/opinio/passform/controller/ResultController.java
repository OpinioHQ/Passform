package com.opinio.passform.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.result.FormResults;
import com.opinio.passform.domain.result.ResponseDetail;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.ResultService;

// 제작자 전용(SPEC 5절 234·236행). 비로그인은 보안 필터가 401로 막는다.
@RestController
public class ResultController {

	private final ResultService results;

	private final CurrentUserService currentUser;

	public ResultController(ResultService results, CurrentUserService currentUser) {
		this.results = results;
		this.currentUser = currentUser;
	}

	@GetMapping("/forms/{formId}/results")
	public FormResults results(@PathVariable String formId) {
		UUID userId = currentUser.requireUserId();
		return results.results(userId, formIdOf(formId));
	}

	// 응답 ID 형식이 틀려도 소유권(403)을 먼저 판정하도록 null로 넘기고, service가 없는 응답(404)으로 처리한다.
	@GetMapping("/forms/{formId}/responses/{responseId}")
	public ResponseDetail response(@PathVariable String formId, @PathVariable String responseId) {
		UUID userId = currentUser.requireUserId();
		return results.response(userId, formIdOf(formId), parse(responseId));
	}

	// UUID 형식이 아닌 폼 ID는 그런 폼이 없는 것이라 404다.
	private static UUID formIdOf(String value) {
		UUID id = parse(value);
		if (id == null) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
		return id;
	}

	private static UUID parse(String value) {
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

}
