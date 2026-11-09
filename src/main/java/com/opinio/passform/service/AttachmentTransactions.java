package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormAttachments;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.AttachmentService.AddCommand;
import com.opinio.passform.service.AttachmentService.DownloadCommand;
import com.opinio.passform.service.AttachmentService.RemoveCommand;
import com.opinio.passform.service.AttachmentService.UploadedAttachment;

// 첨부의 DB 구간. S3를 부르는 AttachmentService와 나눠, 업로드와 주소 생성 동안 DB 연결과 행 잠금을 쥐지 않는다.
// 잠금 순서는 폼 행 → 소유자 행이다.
@Service
public class AttachmentTransactions {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final AttachmentRepository attachments;

	private final ResponseRepository responses;

	private final FormService formService;

	private final StorageQuotaService quota;

	private final Clock clock;

	public AttachmentTransactions(FormRepository forms, FormVersionRepository versions, AttachmentRepository attachments,
			ResponseRepository responses, FormService formService, StorageQuotaService quota, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.attachments = attachments;
		this.responses = responses;
		this.formService = formService;
		this.quota = quota;
		this.clock = clock;
	}

	// 파일 없이 판정할 수 있는 것을 업로드 전에 본다. 지금 사용량을 돌려준다.
	@Transactional(readOnly = true)
	public long checkBeforeUpload(AddCommand command) {
		Form form = forms.findById(FormService.parseId(command.formId())).orElseThrow(AttachmentTransactions::notFound);
		FormService.requireOwner(form, command.userId());
		requireLocation(form, command.questionId());
		return attachments.usedBytes(form.getOwnerId());
	}

	// 잠금 안의 최종 판정. 실패하면 호출자가 올린 원본을 지운다.
	@Transactional
	public UploadedAttachment saveUploaded(AddCommand command, String filename, String contentType, String key) {
		Form form = lockedOwnedForm(command.formId(), command.userId());
		long before = quota.lockAndMeasure(form.getOwnerId());
		requireLocation(form, command.questionId());
		Instant now = clock.instant();
		Attachment saved = attachments.save(new Attachment(form.getId(), null, command.questionId(), filename,
				contentType, command.sizeBytes(), key, now));

		Map<String, Object> item = new LinkedHashMap<>();
		item.put("attachment_id", saved.getId().toString());
		item.put("filename", filename);
		item.put("content_type", contentType);
		item.put("size_bytes", command.sizeBytes());
		formService.replaceAttachmentLists(form,
				FormAttachments.withAdded(form.getWorkingDefinition(), command.questionId(), item));
		quota.requireWithinLimit(form.getOwnerId(), before);
		return new UploadedAttachment(saved.getId(), filename, contentType, command.sizeBytes());
	}

	// 정의의 목록에서 뺀다. 과거 버전이 참조하면 원본과 사용량을 유지한다(SPEC 5절 170행, AC63).
	@Transactional
	public void remove(RemoveCommand command) {
		Form form = lockedOwnedForm(command.formId(), command.userId());
		long before = quota.lockAndMeasure(form.getOwnerId());
		if (!FormAttachments.contains(form.getWorkingDefinition(), command.questionId(), command.attachmentId())) {
			throw notFound();
		}
		formService.replaceAttachmentLists(form,
				FormAttachments.withRemoved(form.getWorkingDefinition(), command.questionId(), command.attachmentId()));
		quota.releaseUnreferenced(form.getId(), Set.of(command.attachmentId()));
		quota.requireWithinLimit(form.getOwnerId(), before);
	}

	@Transactional(readOnly = true)
	public Attachment readableAttachment(DownloadCommand command) {
		UUID formId = FormService.parseId(command.formId());
		UUID attachmentId = parseAttachmentId(command.attachmentId());
		Form form = forms.findById(formId).filter(found -> !found.isDeleted()).orElseThrow(AttachmentTransactions::notFound);
		// 경로의 폼·문항과 맞는 제작자 첨부만 내준다.
		Attachment attachment = attachments.findById(attachmentId)
			.filter(found -> found.getFormId().equals(formId) && found.getResponseId() == null)
			.filter(found -> Objects.equals(found.getQuestionId(), command.questionId()))
			.orElseThrow(AttachmentTransactions::notFound);
		String id = attachmentId.toString();
		List<Integer> holding = versions.findByFormId(formId)
			.stream()
			.filter(version -> FormAttachments.contains(version.getDefinition(), command.questionId(), id))
			.map(FormVersion::getVersion)
			.toList();
		boolean inWorking = FormAttachments.contains(form.getWorkingDefinition(), command.questionId(), id);
		if (!inWorking && holding.isEmpty()) {
			throw notFound();
		}
		requireReadable(form, holding, command.userId());
		return attachment;
	}

	// 응답 첨부는 그 폼의 제작자와 제출한 로그인 사용자만 받는다(SPEC 5절 198행, AC64). 그 밖의 사람에게는 있는지도 알리지 않는다.
	@Transactional(readOnly = true)
	public Attachment readableResponseFile(UUID userId, String formId, String responseId, String attachmentId) {
		UUID parsedForm = FormService.parseId(formId);
		UUID parsedResponse = parseAttachmentId(responseId);
		Form form = forms.findById(parsedForm).filter(found -> !found.isDeleted()).orElseThrow(AttachmentTransactions::notFound);
		Response response = responses.findById(parsedResponse)
			.filter(found -> found.getFormId().equals(parsedForm))
			.filter(found -> found.getStatus() == ResponseStatus.SUBMITTED)
			.orElseThrow(AttachmentTransactions::notFound);
		if (!form.isOwnedBy(userId) && !userId.equals(response.getUserId())) {
			throw notFound();
		}
		Attachment attachment = attachments.findById(parseAttachmentId(attachmentId))
			.filter(found -> parsedResponse.equals(found.getResponseId()))
			.orElseThrow(AttachmentTransactions::notFound);
		if (!attachment.isAvailable()) {
			throw new DomainException(ErrorCode.ATTACHMENT_REMOVED);
		}
		return attachment;
	}

	private void requireReadable(Form form, List<Integer> holding, Optional<UUID> user) {
		if (user.filter(form::isOwnedBy).isPresent()) {
			return;
		}
		// 초안은 제작자만 본다. 제작자 전용을 다른 사용자가 부르면 403이다(SPEC 5절 90행).
		if (form.getStatus() == FormStatus.DRAFT) {
			throw user.isPresent() ? new DomainException(ErrorCode.FORBIDDEN) : CurrentUserService.noLogin();
		}
		boolean currentAndOpen = form.getStatus() == FormStatus.OPEN && !form.isClosedAt(clock.instant())
				&& holding.contains(form.getDefinitionVersion());
		if (currentAndOpen) {
			return;
		}
		if (user.isEmpty()) {
			throw CurrentUserService.noLogin();
		}
		if (holding.isEmpty() || !responses.existsSubmittedInVersions(user.get(), form.getId(), holding)) {
			throw notFound();
		}
	}

	private Form lockedOwnedForm(String formId, UUID userId) {
		Form form = forms.findByIdForUpdate(FormService.parseId(formId)).orElseThrow(AttachmentTransactions::notFound);
		FormService.requireOwner(form, userId);
		return form;
	}

	private static void requireLocation(Form form, String questionId) {
		if (questionId != null && !FormAttachments.hasQuestion(form.getWorkingDefinition(), questionId)) {
			throw notFound();
		}
	}

	private static UUID parseAttachmentId(String attachmentId) {
		try {
			return UUID.fromString(attachmentId);
		}
		catch (IllegalArgumentException exception) {
			throw notFound();
		}
	}

	private static DomainException notFound() {
		return new DomainException(ErrorCode.NOT_FOUND);
	}

}
