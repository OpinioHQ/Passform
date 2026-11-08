package com.opinio.passform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.InboxService;
import com.opinio.passform.service.InboxService.InboxItem;
import com.opinio.passform.service.ResponseHistoryService;
import com.opinio.passform.service.ResponseHistoryService.HistoryDetail;
import com.opinio.passform.service.ResponseHistoryService.HistoryItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// 내 폼함과 제출 이력(SPEC 5절 192~193행, 199~200행). 로그인 전용이고 현재 사용자 본인의 것만 다룬다.
@RestController
public class MyFormsController {

	private final InboxService inbox;

	private final ResponseHistoryService history;

	private final CurrentUserService currentUser;

	public MyFormsController(InboxService inbox, ResponseHistoryService history, CurrentUserService currentUser) {
		this.inbox = inbox;
		this.history = history;
		this.currentUser = currentUser;
	}

	// 처음 저장하면 201, 이미 저장했으면 200이다.
	@PostMapping("/me/saved-forms")
	public ResponseEntity<Void> save(@Valid @RequestBody SaveRequest request) {
		boolean created = inbox.save(currentUser.requireUserId(), parseId(request.formId()));
		return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
	}

	@GetMapping("/me/forms")
	public List<InboxItem> forms() {
		return inbox.list(currentUser.requireUserId());
	}

	@GetMapping("/me/responses")
	public List<HistoryItem> responses() {
		return history.list(currentUser.requireUserId());
	}

	@GetMapping("/me/responses/{responseId}")
	public HistoryDetail response(@PathVariable String responseId) {
		return history.detail(currentUser.requireUserId(), parseId(responseId));
	}

	// 형식이 틀린 ID도 없는 것과 같은 404다.
	private static UUID parseId(String id) {
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException e) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
	}

	public record SaveRequest(@NotBlank String formId) {
	}

}
