package com.opinio.passform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.FormDeletionService;

// 제작자의 폼 단위 삭제(SPEC 5절 119·237·238행). 설정 editable·cancellable과 무관하다(226행).
@RestController
public class FormDeletionController {

	private final FormDeletionService deletions;

	private final CurrentUserService currentUser;

	public FormDeletionController(FormDeletionService deletions, CurrentUserService currentUser) {
		this.deletions = deletions;
		this.currentUser = currentUser;
	}

	@DeleteMapping("/forms/{formId}/responses")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteResponses(@PathVariable String formId) {
		deletions.deleteReceivedResponses(currentUser.requireUserId(), formId);
	}

	@DeleteMapping("/forms/{formId}/response-attachments")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteResponseAttachments(@PathVariable String formId) {
		deletions.deleteResponseAttachments(currentUser.requireUserId(), formId);
	}

	@DeleteMapping("/forms/{formId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteForm(@PathVariable String formId) {
		deletions.deleteForm(currentUser.requireUserId(), formId);
	}

}
