package com.opinio.passform.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.repository.FormRepository;

// 골든 실행기를 먼저 돌리기 위한 임시 스텁이다. 다음 커밋에서 구현으로 바꾼다.
@Service
public class FormService {

	private final FormRepository forms;

	private final Clock clock;

	public FormService(FormRepository forms, Clock clock) {
		this.forms = forms;
		this.clock = clock;
	}

	@Transactional
	@SuppressWarnings("unchecked")
	public CreatedForm create(CreateCommand command) {
		Form form = forms.save(new Form(command.ownerId(), new LinkedHashMap<>((Map<String, Object>) command.body()),
				clock.instant()));
		return new CreatedForm(form.getId(), "draft");
	}

	@Transactional(readOnly = true)
	public DefinitionView getDefinition(UUID userId, String formId) {
		Form form = forms.findById(UUID.fromString(formId)).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		return new DefinitionView(form.getWorkingDefinition(), form.getStatus().name().toLowerCase(), null,
				form.getDefinitionVersion());
	}

	@Transactional
	public StateView update(UpdateCommand command) {
		throw new UnsupportedOperationException("not implemented");
	}

	public record CreateCommand(UUID ownerId, Object body) {
	}

	public record UpdateCommand(UUID userId, String formId, Object body) {
	}

	public record CreatedForm(UUID formId, String status) {
	}

	public record DefinitionView(Map<String, Object> definition, String status, String shareUrl,
			Integer definitionVersion) {
	}

	public record StateView(String status, String shareUrl, Integer definitionVersion) {
	}

}
