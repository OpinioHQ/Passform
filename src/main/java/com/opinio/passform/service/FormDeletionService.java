package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;

// 받은 응답 전체 삭제, 응답 첨부 전체 삭제, 폼 삭제(SPEC 5절 119·237·238행, 224행, AC66, AC67, AC70).
// 각 작업은 한 트랜잭션이라 일부만 처리된 상태가 보이지 않는다. S3 원본은 커밋 뒤에만 지운다.
// 폼 행을 잠가 같은 폼의 제출과 한 번에 하나씩 처리한다. 잠금 순서는 폼 행 → 소유자 행이다.
@Service
public class FormDeletionService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final AttachmentRepository attachments;

	private final SavedFormRepository savedForms;

	private final StorageQuotaService quota;

	private final Clock clock;

	public FormDeletionService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			AttachmentRepository attachments, SavedFormRepository savedForms, StorageQuotaService quota, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.attachments = attachments;
		this.savedForms = savedForms;
		this.quota = quota;
		this.clock = clock;
	}

	// 폼 정의와 공유 링크는 유지한다. 작성 중 응답은 대상이 아니다(SPEC 5절 208행, AC83).
	@Transactional
	public void deleteReceivedResponses(UUID userId, String formId) {
		Form form = lockedOwnedForm(userId, formId);
		quota.releaseKeys(form.getId(), removeReceived(form, clock.instant()));
	}

	// 답변과 응답 수는 그대로 두고 원본만 지운다. 응답·응답자를 골라 지우는 기능은 없다(SPEC 5절 44행, AC70).
	@Transactional
	public void deleteResponseAttachments(UUID userId, String formId) {
		Form form = lockedOwnedForm(userId, formId);
		List<UUID> received = responses.findSubmittedNotDeletedByFormId(form.getId()).stream().map(Response::getId).toList();
		List<String> keys = new ArrayList<>();
		for (Attachment file : received.isEmpty() ? List.<Attachment>of() : attachments.findByResponseIdIn(received)) {
			if (file.isAvailable()) {
				keys.add(file.getS3Key());
				file.markRemoved();
			}
		}
		quota.releaseKeys(form.getId(), keys);
	}

	// 정의·버전·받은 응답·첨부를 지우되 이력이 form_id를 돌려주므로 forms 행은 삭제 시각과 함께 남긴다(ARCHITECTURE 10절).
	// 사람 결정(#63): 내 폼함 항목은 지운다. 제출 내용은 제출 이력 화면에서 본다.
	@Transactional
	public void deleteForm(UUID userId, String formId) {
		Form form = lockedOwnedForm(userId, formId);
		Instant now = clock.instant();
		List<String> keys = new ArrayList<>(removeReceived(form, now));
		responses.deleteAllInBatch(responses.findNotSubmittedByFormId(form.getId()));
		savedForms.deleteByFormId(form.getId());
		List<Attachment> creatorFiles = attachments.findByFormIdAndResponseIdIsNull(form.getId());
		creatorFiles.forEach(file -> keys.add(file.getS3Key()));
		attachments.deleteAllInBatch(creatorFiles);
		versions.deleteAllInBatch(versions.findByFormId(form.getId()));
		form.markDeleted(now);
		quota.releaseKeys(form.getId(), keys);
	}

	// 로그인 응답은 제출 버전의 문구를 복사해 조회 전용 이력으로 남기고, 비로그인 응답은 첨부와 함께 지운다. 지울 원본의 키를 돌려준다.
	private List<String> removeReceived(Form form, Instant now) {
		List<Response> received = responses.findSubmittedNotDeletedByFormId(form.getId());
		if (received.isEmpty()) {
			return List.of();
		}
		Map<Integer, Map<String, Object>> definitions = versions.findByFormId(form.getId())
			.stream()
			.collect(Collectors.toMap(FormVersion::getVersion, FormVersion::getDefinition));
		Map<UUID, List<Attachment>> filesByResponse = attachments
			.findByResponseIdIn(received.stream().map(Response::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(Attachment::getResponseId));

		List<String> keys = new ArrayList<>();
		List<Response> anonymous = new ArrayList<>();
		List<Attachment> anonymousFiles = new ArrayList<>();
		for (Response response : received) {
			List<Attachment> files = filesByResponse.getOrDefault(response.getId(), List.of());
			files.stream().filter(Attachment::isAvailable).forEach(file -> keys.add(file.getS3Key()));
			if (response.getUserId() == null) {
				anonymous.add(response);
				anonymousFiles.addAll(files);
			}
			else {
				Map<String, Object> definition = definitions.getOrDefault(response.getFormVersion(), Map.of());
				response.markCreatorDeleted(now, InboxService.titleOf(definition), historyQuestions(definition));
				files.forEach(Attachment::markRemoved);
			}
		}
		// 첨부가 응답을 참조하므로 먼저 지운다.
		attachments.deleteAllInBatch(anonymousFiles);
		responses.deleteAllInBatch(anonymous);
		return keys;
	}

	// ResponseHistoryService가 읽는 모양이다: {question_id, question_note}.
	private static List<Map<String, Object>> historyQuestions(Map<String, Object> definition) {
		if (definition.isEmpty()) {
			return List.of();
		}
		Set<String> seen = new HashSet<>();
		List<Map<String, Object>> questions = new ArrayList<>();
		for (FormDefinition.Question question : FormDefinition.from(definition).questions()) {
			if (seen.add(question.questionId())) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("question_id", question.questionId());
				item.put("question_note", question.questionNote());
				questions.add(item);
			}
		}
		return questions;
	}

	private Form lockedOwnedForm(UUID userId, String formId) {
		Form form = forms.findByIdForUpdate(FormService.parseId(formId))
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		FormService.requireOwner(form, userId);
		quota.lockAndMeasure(form.getOwnerId());
		return form;
	}

}
