package com.opinio.passform.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.FormQrService;
import com.opinio.passform.service.FormService;
import com.opinio.passform.service.AttachmentService;
import com.opinio.passform.service.FormService.BranchGraphView;
import com.opinio.passform.service.FormService.CreateCommand;
import com.opinio.passform.service.FormService.CreatedForm;
import com.opinio.passform.service.FormService.CreatedFormView;
import com.opinio.passform.service.FormService.StateView;
import com.opinio.passform.service.FormService.UpdateCommand;

// 폼 제작 API(SPEC 5절 115~120행). 본문의 형식 검사는 form.schema.json이 맡으므로(AGENTS 5절) DTO 없이 JSON 그대로 넘긴다.
@RestController
public class FormController {

	private final FormService forms;

	private final AttachmentService attachments;

	private final FormQrService qrCodes;

	private final CurrentUserService currentUser;

	public FormController(FormService forms, AttachmentService attachments, FormQrService qrCodes,
			CurrentUserService currentUser) {
		this.forms = forms;
		this.attachments = attachments;
		this.qrCodes = qrCodes;
		this.currentUser = currentUser;
	}

	@PostMapping("/forms")
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedForm create(@RequestBody Object body) {
		return forms.create(new CreateCommand(currentUser.requireUserId(), body));
	}

	@PostMapping("/forms/{formId}/copy")
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedForm copy(@PathVariable String formId) {
		return attachments.copyForm(currentUser.requireUserId(), formId);
	}

	@GetMapping("/forms/{formId}/definition")
	public Map<String, Object> definition(@PathVariable String formId) {
		return forms.getDefinition(currentUser.requireUserId(), formId).toBody();
	}

	@PatchMapping("/forms/{formId}")
	public StateView update(@PathVariable String formId, @RequestBody Object body) {
		return forms.update(new UpdateCommand(currentUser.requireUserId(), formId, body));
	}

	@GetMapping("/forms/{formId}/branch-graph")
	public BranchGraphView branchGraph(@PathVariable String formId) {
		return forms.branchGraph(currentUser.requireUserId(), formId);
	}

	@GetMapping("/forms/{formId}/qr")
	public ResponseEntity<byte[]> qr(@PathVariable String formId) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.body(qrCodes.png(currentUser.requireUserId(), formId));
	}

	@GetMapping("/me/created-forms")
	public List<CreatedFormView> createdForms() {
		return forms.listCreated(currentUser.requireUserId());
	}

}
