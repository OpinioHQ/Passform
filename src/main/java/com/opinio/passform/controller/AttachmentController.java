package com.opinio.passform.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.opinio.passform.service.AttachmentService;
import com.opinio.passform.service.AttachmentService.AddCommand;
import com.opinio.passform.service.AttachmentService.DownloadCommand;
import com.opinio.passform.service.AttachmentService.RemoveCommand;
import com.opinio.passform.service.AttachmentService.UploadedAttachment;
import com.opinio.passform.service.CurrentUserService;

// 폼 설명란·문항 첨부 API(SPEC 5절 121~124행, 196~197행).
@RestController
public class AttachmentController {

	private final AttachmentService attachments;

	private final CurrentUserService currentUser;

	public AttachmentController(AttachmentService attachments, CurrentUserService currentUser) {
		this.attachments = attachments;
		this.currentUser = currentUser;
	}

	@PostMapping("/forms/{formId}/attachments")
	@ResponseStatus(HttpStatus.CREATED)
	public UploadedAttachment addNote(@PathVariable String formId, @RequestPart("file") MultipartFile file) {
		return add(formId, null, file);
	}

	@PostMapping("/forms/{formId}/questions/{questionId}/attachments")
	@ResponseStatus(HttpStatus.CREATED)
	public UploadedAttachment addQuestion(@PathVariable String formId, @PathVariable String questionId,
			@RequestPart("file") MultipartFile file) {
		return add(formId, questionId, file);
	}

	@DeleteMapping("/forms/{formId}/attachments/{attachmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeNote(@PathVariable String formId, @PathVariable String attachmentId) {
		attachments.remove(new RemoveCommand(currentUser.requireUserId(), formId, null, attachmentId));
	}

	@DeleteMapping("/forms/{formId}/questions/{questionId}/attachments/{attachmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeQuestion(@PathVariable String formId, @PathVariable String questionId,
			@PathVariable String attachmentId) {
		attachments.remove(new RemoveCommand(currentUser.requireUserId(), formId, questionId, attachmentId));
	}

	@GetMapping("/forms/{formId}/attachments/{attachmentId}")
	public ResponseEntity<Void> downloadNote(@PathVariable String formId, @PathVariable String attachmentId) {
		return download(formId, null, attachmentId);
	}

	@GetMapping("/forms/{formId}/questions/{questionId}/attachments/{attachmentId}")
	public ResponseEntity<Void> downloadQuestion(@PathVariable String formId, @PathVariable String questionId,
			@PathVariable String attachmentId) {
		return download(formId, questionId, attachmentId);
	}

	private UploadedAttachment add(String formId, String questionId, MultipartFile file) {
		return attachments.add(new AddCommand(currentUser.requireUserId(), formId, questionId,
				file.getOriginalFilename(), file.getSize(), file));
	}

	// 권한을 확인한 뒤 짧게 유효한 주소로 보낸다. 주소가 캐시에 남지 않게 한다.
	private ResponseEntity<Void> download(String formId, String questionId, String attachmentId) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(attachments.downloadUrl(new DownloadCommand(currentUser.findUserId(), formId, questionId, attachmentId)))
			.cacheControl(CacheControl.noStore())
			.build();
	}

}
