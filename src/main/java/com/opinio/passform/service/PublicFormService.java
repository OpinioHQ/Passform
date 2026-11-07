package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.ConsentItems;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormPublishValidator;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.template.ConsentNotices;
import com.opinio.passform.domain.template.ConsentTemplate;
import com.opinio.passform.repository.ConsentTemplateRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;

// 폼 공개 조회(SPEC 5절 187행). 응답자가 보는 정의는 최신 배포 버전에서, 운영 설정은 현재 값에서 읽는다(156행).
@Service
public class PublicFormService {

	private static final List<String> UNBRANCHED_MODES = List.of("one_question", "all_questions");

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ConsentTemplateRepository consentTemplates;

	private final Clock clock;

	public PublicFormService(FormRepository forms, FormVersionRepository versions,
			ConsentTemplateRepository consentTemplates, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.consentTemplates = consentTemplates;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PublicFormView findBySlug(String slug) {
		return view(forms.findBySlug(slug));
	}

	@Transactional(readOnly = true)
	public PublicFormView findById(String formId) {
		return view(forms.findById(FormService.parseId(formId)));
	}

	private PublicFormView view(Optional<Form> found) {
		Form form = found.filter(candidate -> !candidate.isHiddenFromPublic())
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (form.isClosedAt(clock.instant())) {
			throw new DomainException(ErrorCode.FORM_CLOSED);
		}
		Map<String, Object> definition = versions.findById(new FormVersionId(form.getId(), form.getDefinitionVersion()))
			.orElseThrow(() -> new IllegalStateException("배포 버전의 정의가 없다"))
			.getDefinition();
		ConsentTemplate template = consentTemplates.findById((String) definition.get("consent_template_id"))
			.orElseThrow(() -> new IllegalStateException("배포 버전의 동의 템플릿이 없다"));

		String defaultMode = FormPublishValidator.defaultMode(definition);
		List<String> allowedModes = FormPublishValidator.hasBranchRules(definition) ? List.of(defaultMode)
				: UNBRANCHED_MODES;
		Consent consent = new Consent(ConsentItems.of(definition), template.getPurpose(), template.getRetention(),
				ConsentNotices.REFUSAL_NOTICE, form.getAiSummaryNotice());
		return new PublicFormView(form.getId(), form.getDefinitionVersion(), (String) definition.get("title"),
				(String) definition.get("note"), definition.get("note_attachments"), form.getDeadline(),
				definition.get("sections"), new Display(defaultMode, allowedModes),
				(String) definition.get("payment_link"), consent);
	}

	public record PublicFormView(UUID formId, int definitionVersion, String title, String note, Object noteAttachments,
			Instant deadline, Object sections, Display display, String paymentLink, Consent consent) {
	}

	public record Display(String defaultMode, List<String> allowedModes) {
	}

	public record Consent(List<String> items, String purpose, String retention, String refusalNotice,
			Map<String, Object> aiSummary) {
	}

}
