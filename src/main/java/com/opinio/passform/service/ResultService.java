package com.opinio.passform.service;

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
import com.opinio.passform.domain.result.ResultStatistics;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;

// 결과 통계와 응답 상세(SPEC 5절 234·236행). 응답은 findSubmittedNotDeletedByFormId로만 읽는다(AC20).
@Service
public class ResultService {

	private final FormRepository forms;

	private final FormVersionRepository formVersions;

	private final ResponseRepository responses;

	public ResultService(FormRepository forms, FormVersionRepository formVersions, ResponseRepository responses) {
		this.forms = forms;
		this.formVersions = formVersions;
		this.responses = responses;
	}

	@Transactional(readOnly = true)
	public FormResults results(UUID userId, UUID formId) {
		requireOwnedForm(userId, formId);
		List<ResponseRecord> records = responses.findSubmittedNotDeletedByFormId(formId)
			.stream()
			.map(Response::toResponseRecord)
			.toList();
		Map<Integer, List<DefinitionQuestion>> definitions = new LinkedHashMap<>();
		for (ResponseRecord record : records) {
			definitions.computeIfAbsent(record.formVersion(), version -> questionsOf(formId, version));
		}
		return ResultStatistics.calculate(definitions, records);
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
		return ResponseDetail.of(response, questionsOf(formId, response.getFormVersion()));
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
