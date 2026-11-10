package com.opinio.passform.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.opinio.passform.controller.ResponseChangeController.EditRequest;
import com.opinio.passform.controller.SubmissionController.SubmissionRequest;
import com.opinio.passform.controller.SubmissionController.SubmissionResponse;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.ResponseChangeService.EditCommand;
import com.opinio.passform.service.ResponseChangeService.EditedResponse;
import com.opinio.passform.service.ResponseFileService;
import com.opinio.passform.service.ResponseFileService.UploadedFile;
import com.opinio.passform.service.SubmitCommand;

import jakarta.validation.Valid;

// 파일이 있는 제출·수정과 응답 첨부 다운로드(SPEC 5절 188·194·198행).
// multipart의 data 부분은 JSON API와 같은 본문이고, files와 file_question_ids는 같은 순서로 짝을 이룬다.
@RestController
public class ResponseFileController {

	private final ResponseFileService files;

	private final CurrentUserService currentUser;

	public ResponseFileController(ResponseFileService files, CurrentUserService currentUser) {
		this.files = files;
		this.currentUser = currentUser;
	}

	@PostMapping(path = "/forms/{formId}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public SubmissionResponse submit(@PathVariable String formId, @Valid @RequestPart("data") SubmissionRequest request,
			@RequestPart(name = "files", required = false) List<MultipartFile> parts,
			@RequestParam(name = "file_question_ids", required = false) List<String> questionIds) {
		SubmitCommand command = new SubmitCommand(parseId(formId), currentUser.findUserId().orElse(null),
				request.formVersion(), answersOf(request.answers()), request.consentAgreed(), request.aiSummaryAgreed(),
				request.draftResponseId());
		return SubmissionResponse.from(files.submit(command, uploads(parts, questionIds)));
	}

	@PatchMapping(path = "/me/responses/{responseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public EditedResponse edit(@PathVariable String responseId, @Valid @RequestPart("data") EditRequest request,
			@RequestPart(name = "files", required = false) List<MultipartFile> parts,
			@RequestParam(name = "file_question_ids", required = false) List<String> questionIds) {
		EditCommand command = new EditCommand(currentUser.requireUserId(), parseId(responseId), answersOf(request.answers()),
				request.attachments(), List.of());
		return files.edit(command, uploads(parts, questionIds));
	}

	@GetMapping("/forms/{formId}/responses/{responseId}/attachments/{attachmentId}")
	public ResponseEntity<Void> download(@PathVariable String formId, @PathVariable String responseId,
			@PathVariable String attachmentId) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(files.downloadUrl(currentUser.requireUserId(), formId, responseId, attachmentId))
			.cacheControl(CacheControl.noStore())
			.build();
	}

	private static List<Answer> answersOf(List<SubmissionController.AnswerRequest> answers) {
		return answers.stream().map(answer -> new Answer(answer.questionId(), answer.value())).toList();
	}

	private static List<UploadedFile> uploads(List<MultipartFile> parts, List<String> questionIds) {
		List<MultipartFile> files = parts == null ? List.of() : parts;
		List<String> ids = questionIds == null ? List.of() : questionIds;
		if (files.size() != ids.size()) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		List<UploadedFile> uploads = new ArrayList<>();
		for (int i = 0; i < files.size(); i++) {
			MultipartFile file = files.get(i);
			uploads.add(new UploadedFile(ids.get(i), file.getOriginalFilename(), file.getSize(), file));
		}
		return uploads;
	}

	private static UUID parseId(String id) {
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException e) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
	}

}
