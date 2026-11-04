package com.opinio.passform.demo.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.demo.domain.ForbiddenException;
import com.opinio.passform.demo.domain.NotFoundException;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.FormJson;
import com.opinio.passform.demo.domain.form.FormVersion;
import com.opinio.passform.demo.domain.response.Response;
import com.opinio.passform.demo.repository.FormRepository;
import com.opinio.passform.demo.repository.FormVersionRepository;
import com.opinio.passform.demo.repository.ResponseRepository;

// 제작자 권한은 Form.owner_id로 판정한다. 집계 대상은 현재 남은 submitted 응답뿐이다(AC2, AC20).
@Component
public class FormSnapshotLoader {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	public FormSnapshotLoader(FormRepository forms, FormVersionRepository versions, ResponseRepository responses) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
	}

	@Transactional(readOnly = true)
	public FormSnapshot loadOwned(String formId, String userId) {
		Form form = forms.findById(formId).orElseThrow(NotFoundException::new);
		if (!form.isOwnedBy(userId)) {
			throw new ForbiddenException();
		}
		Map<Integer, FormDefinition> definitions = new TreeMap<>();
		for (FormVersion version : versions.findByFormIdOrderByVersionAsc(formId)) {
			definitions.put(version.getVersion(), version.definition());
		}
		List<Response> submitted = responses.findByFormIdAndStatus(formId, ResponseStatus.SUBMITTED);
		List<ResponseRecord> records = submitted.stream()
			.map(response -> new ResponseRecord(response.getId(), response.getFormVersion(), response.getStatus(),
					FormJson.parseAnswers(response.getAnswersJson())))
			.toList();
		return new FormSnapshot(form, definitions, submitted, records);
	}

}
