package com.opinio.passform.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.PublicFormService.PublicFormView;

// 제출 이력(SPEC 5절 192~193행, AC15). 본인의 submitted 응답만 보인다. 남의 것·없는 것·작성 중인 것은 같은 404다(AC2).
// 제목·문항 문구는 그 응답의 form_version 정의에서 읽는다. editable·cancellable은 폼 설정 값을 그대로 준다(동작은 T10).
@Service
public class ResponseHistoryService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final AttachmentRepository attachments;

	private final PublicFormService publicForms;

	public ResponseHistoryService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			AttachmentRepository attachments, PublicFormService publicForms) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.attachments = attachments;
		this.publicForms = publicForms;
	}

	@Transactional(readOnly = true)
	public List<HistoryItem> list(UUID userId) {
		List<Response> submitted = responses.findSubmittedByUserId(userId);
		if (submitted.isEmpty()) {
			return List.of();
		}
		Map<UUID, Form> formsById = forms.findAllById(submitted.stream().map(Response::getFormId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Form::getId, Function.identity()));
		Map<FormVersionId, Map<String, Object>> definitions = versions
			.findAllById(submitted.stream().map(ResponseHistoryService::versionIdOf).distinct().toList())
			.stream()
			.collect(Collectors.toMap(FormVersion::getId, FormVersion::getDefinition));
		return submitted.stream().map(response -> {
			Form form = formsById.get(response.getFormId());
			return new HistoryItem(response.getId(), response.getFormId(),
					titleOf(response, definitions.get(versionIdOf(response))), response.getFormVersion(),
					response.getSubmittedAt(), response.getUpdatedAt(), form != null && form.isEditable(),
					form != null && form.isCancellable());
		}).toList();
	}

	@Transactional(readOnly = true)
	public HistoryDetail detail(UUID userId, UUID responseId) {
		Response response = responses.findById(responseId)
			.filter(found -> found.getStatus() == ResponseStatus.SUBMITTED && userId.equals(found.getUserId()))
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		Form form = forms.findById(response.getFormId()).filter(found -> !found.isDeleted()).orElse(null);
		Map<String, Object> definition = form == null ? null
				: versions.findById(versionIdOf(response)).map(FormVersion::getDefinition).orElse(null);
		// 폼이 삭제돼 정의가 없으면 조회 전용 이력이다(form_definition: null). 화면은 T12가 맡는다.
		PublicFormView view = definition == null ? null : publicForms.viewOfVersion(form, response.getFormVersion());
		Map<String, String> notes = definition == null ? historyNotes(response) : notesOf(definition);
		List<ContentItem> content = response.getContent()
			.stream()
			.map(answer -> new ContentItem(answer.questionId(), notes.get(answer.questionId()), answer.value()))
			.toList();
		List<AttachmentItem> files = attachments.findByResponseIdOrderByCreatedAtAscIdAsc(response.getId())
			.stream()
			.map(AttachmentItem::of)
			.toList();
		return new HistoryDetail(titleOf(response, definition), response.getFormVersion(), view, content, files,
				response.getSubmittedAt(), response.getUpdatedAt(), form != null && form.isEditable(),
				form != null && form.isCancellable());
	}

	private static FormVersionId versionIdOf(Response response) {
		return new FormVersionId(response.getFormId(), response.getFormVersion());
	}

	// 삭제 뒤 남긴 제목이 있으면 그것을, 아니면 제출 버전의 제목을 쓴다.
	private static String titleOf(Response response, Map<String, Object> definition) {
		if (response.getHistoryFormTitle() != null) {
			return response.getHistoryFormTitle();
		}
		return definition == null ? "" : InboxService.titleOf(definition);
	}

	private static Map<String, String> notesOf(Map<String, Object> definition) {
		Map<String, String> notes = new HashMap<>();
		for (Question question : FormDefinition.from(definition).questions()) {
			notes.putIfAbsent(question.questionId(), question.questionNote());
		}
		return notes;
	}

	private static Map<String, String> historyNotes(Response response) {
		Map<String, String> notes = new HashMap<>();
		if (response.getHistoryQuestions() != null) {
			for (Map<String, Object> question : response.getHistoryQuestions()) {
				if (question.get("question_id") instanceof String id && question.get("question_note") instanceof String note) {
					notes.putIfAbsent(id, note);
				}
			}
		}
		return notes;
	}

	public record HistoryItem(UUID responseId, UUID formId, String formTitle, int formVersion, Instant submittedAt,
			Instant updatedAt, boolean editable, boolean cancellable) {
	}

	public record HistoryDetail(String formTitle, int formVersion, PublicFormView formDefinition,
			List<ContentItem> content, List<AttachmentItem> attachments, Instant submittedAt, Instant updatedAt,
			boolean editable, boolean cancellable) {
	}

	public record ContentItem(String questionId, String questionNote, Object value) {
	}

	// 다운로드 주소는 주지 않는다(응답 첨부 다운로드는 이 이슈 범위 밖).
	public record AttachmentItem(UUID attachmentId, String questionId, String filename, String contentType,
			long sizeBytes, boolean available) {

		static AttachmentItem of(Attachment attachment) {
			return new AttachmentItem(attachment.getId(), attachment.getQuestionId(), attachment.getFilename(),
					attachment.getContentType(), attachment.getSizeBytes(), attachment.isAvailable());
		}

	}

}
