package com.opinio.passform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.controller.SubmissionController.AnswerRequest;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.ResponseChangeService;
import com.opinio.passform.service.ResponseChangeService.EditCommand;
import com.opinio.passform.service.ResponseChangeService.EditedResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 제출 후 수정·취소(SPEC 5절 194~195행). 로그인 전용이고 본인의 제출 응답만 다룬다.
@RestController
public class ResponseChangeController {

	private final ResponseChangeService changes;

	private final CurrentUserService currentUser;

	public ResponseChangeController(ResponseChangeService changes, CurrentUserService currentUser) {
		this.changes = changes;
		this.currentUser = currentUser;
	}

	@PatchMapping("/me/responses/{responseId}")
	public EditedResponse edit(@PathVariable String responseId, @Valid @RequestBody EditRequest request) {
		List<Answer> answers = request.answers()
			.stream()
			.map(answer -> new Answer(answer.questionId(), answer.value()))
			.toList();
		return changes.edit(new EditCommand(currentUser.requireUserId(), parseId(responseId), answers,
				request.attachments(), List.of()));
	}

	@DeleteMapping("/me/responses/{responseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable String responseId) {
		changes.cancel(currentUser.requireUserId(), parseId(responseId));
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

	// attachments는 남길 attachment_id 목록이다. 생략하면 기존 파일을 모두 유지한다(SPEC 5절 222행).
	public record EditRequest(@NotNull List<@NotNull @Valid AnswerRequest> answers, List<@NotNull String> attachments) {
	}

}
