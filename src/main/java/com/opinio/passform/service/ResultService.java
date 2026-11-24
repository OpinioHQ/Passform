package com.opinio.passform.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.result.FormResults;
import com.opinio.passform.domain.result.ResponseDetail;
import com.opinio.passform.domain.result.ResponseList;
import com.opinio.passform.domain.result.ResultStatistics;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.ResponseRepository;

// 결과 통계와 응답 상세(SPEC 5절 234·236행). 응답은 findSubmittedNotDeletedByFormId로만 읽는다(AC20).
@Service
public class ResultService {

	// 응답별 보기 순서. 응답 ID는 문자열로 비교한다. UUID.compareTo는 부호 있는 비교라 DB의 uuid 순서와 다르다.
	private static final Comparator<Response> SUBMITTED_FIRST = Comparator
		.comparing(Response::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(response -> response.getId().toString());

	private final FormRepository forms;

	private final FormVersionRepository formVersions;

	private final ResponseRepository responses;

	private final AttachmentRepository attachments;

	public ResultService(FormRepository forms, FormVersionRepository formVersions, ResponseRepository responses,
			AttachmentRepository attachments) {
		this.forms = forms;
		this.formVersions = formVersions;
		this.responses = responses;
		this.attachments = attachments;
	}

	@Transactional(readOnly = true)
	public FormResults results(UUID userId, UUID formId) {
		requireOwnedForm(userId, formId);
		List<Response> submitted = responses.findSubmittedNotDeletedByFormId(formId);
		List<ResponseRecord> records = submitted.stream().map(Response::toResponseRecord).toList();
		Map<String, Instant> submittedAt = new HashMap<>();
		for (Response response : submitted) {
			submittedAt.put(response.getId().toString(), response.getSubmittedAt());
		}
		Map<Integer, List<DefinitionQuestion>> definitions = new LinkedHashMap<>();
		for (ResponseRecord record : records) {
			definitions.computeIfAbsent(record.formVersion(), version -> questionsOf(formId, version));
		}
		return ResultStatistics.calculate(definitions, records, submittedAt);
	}

	// 응답별 보기의 목록(AC19, AC62). 대상은 결과와 같은 쿼리로 읽고, 그 순서(버전 → 제출 시각 → ID)는 내보내기가 쓰므로
	// 쿼리는 그대로 두고 여기서 제출 시각 오름차순 → 응답 ID 오름차순으로 다시 정렬한다(사람 결정).
	@Transactional(readOnly = true)
	public ResponseList responses(UUID userId, UUID formId) {
		requireOwnedForm(userId, formId);
		return new ResponseList(responses.findSubmittedNotDeletedByFormId(formId)
			.stream()
			.sorted(SUBMITTED_FIRST)
			.map(Response::getId)
			.toList());
	}

	// 다른 폼의 응답, 제출되지 않았거나 제작자가 삭제한 응답은 없는 응답이라 404다(AC62).
	@Transactional(readOnly = true)
	public ResponseDetail response(UUID userId, UUID formId, UUID responseId) {
		requireOwnedForm(userId, formId);
		Response response = responses.findSubmittedNotDeletedByFormId(formId)
			.stream()
			.filter(candidate -> candidate.getId().equals(responseId))
			.findFirst()
			.orElseThrow(ResultService::notFound);
		List<Map<String, Object>> files = attachments.findByResponseIdOrderByCreatedAtAscIdAsc(responseId)
			.stream()
			.map(ResultService::attachmentItem)
			.toList();
		return ResponseDetail.of(response, questionsOf(formId, response.getFormVersion()), files);
	}

	private static Map<String, Object> attachmentItem(Attachment file) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("question_id", file.getQuestionId());
		item.put("attachment_id", file.getId());
		item.put("filename", file.getFilename());
		item.put("content_type", file.getContentType());
		item.put("size_bytes", file.getSizeBytes());
		item.put("available", file.isAvailable());
		return item;
	}

	// 없거나 삭제된 폼은 404, 남의 폼은 403이다(SPEC 5절 90행, AC2, AC62).
	private Form requireOwnedForm(UUID userId, UUID formId) {
		Form form = forms.findById(formId).filter(found -> !found.isDeleted()).orElseThrow(ResultService::notFound);
		if (!form.isOwnedBy(userId)) {
			throw new DomainException(ErrorCode.FORBIDDEN);
		}
		return form;
	}

	// 응답의 제출 버전 정의다. 최신 정의를 쓰지 않는다(AC19, SPEC 236행).
	private List<DefinitionQuestion> questionsOf(UUID formId, int version) {
		return formVersions.findById(new FormVersionId(formId, version))
			.map(found -> DefinitionQuestion.listOf(found.getDefinition()))
			.orElseThrow(() -> new IllegalStateException("제출 응답의 폼 버전이 없다: " + version));
	}

	private static DomainException notFound() {
		return new DomainException(ErrorCode.NOT_FOUND);
	}

}
