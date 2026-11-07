package com.opinio.passform.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.DraftService;
import com.opinio.passform.service.DraftService.DraftView;
import com.opinio.passform.service.DraftService.SaveResult;
import com.opinio.passform.service.PublicFormService.PublicFormView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 응답 초안(SPEC 5절 189~191행). 로그인 전용이고 현재 사용자 본인의 초안만 다룬다. 다른 사용자의 초안을 가리키는 입력은 받지 않는다.
@RestController
public class DraftController {

	private final DraftService drafts;

	private final CurrentUserService currentUser;

	public DraftController(DraftService drafts, CurrentUserService currentUser) {
		this.drafts = drafts;
		this.currentUser = currentUser;
	}

	@GetMapping("/me/forms/{formId}/response-draft")
	public DraftResponse find(@PathVariable String formId) {
		return DraftResponse.from(drafts.find(currentUser.requireUserId(), parseFormId(formId)));
	}

	// 첫 저장은 201, 이후는 200이다.
	@PutMapping("/me/forms/{formId}/response-draft")
	public ResponseEntity<SaveResponse> save(@PathVariable String formId, @Valid @RequestBody DraftRequest request) {
		List<Answer> answers = request.answers().stream().map(DraftController::answerOf).toList();
		SaveResult result = drafts.save(currentUser.requireUserId(), parseFormId(formId), request.formVersion(), answers);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(new SaveResponse(result.responseId(), "in_progress", result.savedAt()));
	}

	@DeleteMapping("/me/forms/{formId}/response-draft")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String formId) {
		drafts.delete(currentUser.requireUserId(), parseFormId(formId));
	}

	// 미완성 답을 받는다. 값 모양(문자열, 문자열 배열, null)만 보고 제출 검증은 하지 않는다(SPEC 5절 207행).
	private static Answer answerOf(AnswerRequest answer) {
		Object value = answer.value();
		boolean shaped = value == null || value instanceof String
				|| (value instanceof List<?> items && items.stream().allMatch(String.class::isInstance));
		if (!shaped) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		return new Answer(answer.questionId(), value);
	}

	private static UUID parseFormId(String formId) {
		try {
			return UUID.fromString(formId);
		}
		catch (IllegalArgumentException e) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
	}

	public record DraftRequest(@NotNull Integer formVersion, @NotNull List<@NotNull @Valid AnswerRequest> answers) {
	}

	public record AnswerRequest(@NotBlank String questionId, Object value) {
	}

	public record SaveResponse(UUID responseId, String status, Instant savedAt) {
	}

	public record DraftResponse(UUID responseId, int formVersion, PublicFormView formDefinition,
			List<AnswerItem> answers, Instant savedAt) {

		static DraftResponse from(DraftView view) {
			return new DraftResponse(view.responseId(), view.formVersion(), view.formDefinition(),
					view.answers().stream().map(answer -> new AnswerItem(answer.questionId(), answer.value())).toList(),
					view.savedAt());
		}

	}

	public record AnswerItem(String questionId, Object value) {
	}

}
