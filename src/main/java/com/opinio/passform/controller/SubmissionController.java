package com.opinio.passform.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.SubmissionResult;
import com.opinio.passform.service.SubmissionService;
import com.opinio.passform.service.SubmitCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
public class SubmissionController {

	private final SubmissionService submissions;

	private final CurrentUserService currentUser;

	// 로그인 제출은 그 사용자의 응답이다(SPEC 5절 225행). 비로그인이면 null이고, 세션만 있고 User가 없으면 401이다.
	public SubmissionController(SubmissionService submissions, CurrentUserService currentUser) {
		this.submissions = submissions;
		this.currentUser = currentUser;
	}

	@PostMapping("/forms/{formId}/responses")
	@ResponseStatus(HttpStatus.CREATED)
	public SubmissionResponse submit(@PathVariable String formId, @Valid @RequestBody SubmissionRequest request) {
		List<Answer> answers = request.answers()
			.stream()
			.map(answer -> new Answer(answer.questionId(), answer.value()))
			.toList();
		SubmissionResult result = submissions.submit(new SubmitCommand(parseFormId(formId), currentUser.findUserId().orElse(null),
				request.formVersion(), answers, request.consentAgreed(), request.aiSummaryAgreed(),
				request.draftResponseId()));
		return SubmissionResponse.from(result);
	}

	// UUID가 아닌 ID의 폼은 없다.
	private static UUID parseFormId(String formId) {
		try {
			return UUID.fromString(formId);
		}
		catch (IllegalArgumentException e) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
	}

	// consent_agreed는 필수 표시를 하지 않는다. 생략도 service에서 consent_required로 거부한다(AC14).
	// draft_response_id는 로그인한 본인의 작성 중 응답 ID다(SPEC 5절 188행).
	public record SubmissionRequest(@NotNull Integer formVersion, @NotNull List<@NotNull @Valid AnswerRequest> answers,
			Boolean consentAgreed, Boolean aiSummaryAgreed, UUID draftResponseId) {
	}

	public record AnswerRequest(@NotBlank String questionId, Object value) {
	}

	public record SubmissionResponse(UUID responseId, int formVersion, String status, List<ContentItem> content,
			List<Object> attachments, Instant submittedAt, Instant updatedAt) {

		static SubmissionResponse from(SubmissionResult result) {
			List<ContentItem> content = result.content()
				.stream()
				.map(answer -> new ContentItem(answer.questionId(), answer.questionNote(), answer.value()))
				.toList();
			return new SubmissionResponse(result.responseId(), result.formVersion(), "submitted", content, List.of(),
					result.submittedAt(), null);
		}

	}

	public record ContentItem(String questionId, String questionNote, Object value) {
	}

}
