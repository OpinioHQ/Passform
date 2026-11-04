package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.ConflictException;
import com.opinio.passform.domain.InvalidRequestException;
import com.opinio.passform.domain.NotFoundException;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.AnswerValidator;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.FormJson;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.Question;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.response.SavedForm;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;

// POST /forms/{form_id}/responses. 공개 상태·마감·버전·동의·경로·필수·형식을 서버에서 다시 검사한다(SPEC 5절 216행).
@Service
public class SubmissionService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final Clock clock;

	public SubmissionService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			SavedFormRepository savedForms, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.clock = clock;
	}

	public record SubmitCommand(int formVersion, Map<String, List<String>> answers, boolean consentAgreed,
			boolean aiSummaryAgreed) {
	}

	public record AnswerView(String questionId, Object value) {
	}

	public record SubmittedView(String responseId, int formVersion, String status, List<AnswerView> content,
			List<Object> attachments, Instant submittedAt, Instant updatedAt) {
	}

	@Transactional
	public SubmittedView submit(String formId, String userId, SubmitCommand command) {
		Form form = forms.findByIdForUpdate(formId).orElseThrow(NotFoundException::new);
		if (!form.isPublished()) {
			throw new NotFoundException();
		}
		Instant now = clock.instant();
		if (!form.isAcceptingAt(now)) {
			throw new ConflictException("form_closed");
		}
		if (command.formVersion() != form.getDefinitionVersion()) {
			throw new ConflictException("form_version_changed");
		}
		if (!command.consentAgreed()) {
			throw new InvalidRequestException("consent_required");
		}
		FormVersion version = versions.findByFormIdAndVersion(formId, command.formVersion())
			.orElseThrow(NotFoundException::new);
		FormDefinition definition = version.definition();
		Map<String, List<String>> kept = AnswerValidator.validate(definition, command.answers());

		// AI 요약이 꺼진 폼은 보낸 값을 무시하고 false로 저장한다(SPEC 5절 212행).
		boolean aiSummaryAgreed = form.isAiSummaryEnabled() && command.aiSummaryAgreed();
		Response response = new Response(UUID.randomUUID().toString(), formId, userId, command.formVersion(),
				ResponseStatus.SUBMITTED, FormJson.answersToJson(definition, kept), aiSummaryAgreed, now, null);
		responses.save(response);
		if (userId != null) {
			markSubmitted(userId, formId);
		}
		form.closeIfLimitReached(responses.countByFormIdAndStatus(formId, ResponseStatus.SUBMITTED));
		return new SubmittedView(response.getId(), response.getFormVersion(), "submitted", content(definition, kept),
				List.of(), now, null);
	}

	private void markSubmitted(String userId, String formId) {
		savedForms.findById(SavedForm.idOf(userId, formId))
			.ifPresentOrElse(SavedForm::markSubmitted,
					() -> savedForms.save(new SavedForm(userId, formId, SavedForm.SUBMITTED)));
	}

	private static List<AnswerView> content(FormDefinition definition, Map<String, List<String>> answers) {
		List<AnswerView> content = new ArrayList<>();
		answers.forEach((questionId, values) -> {
			Question question = definition.question(questionId).orElse(null);
			content.add(new AnswerView(questionId, FormJson.displayValue(question, values)));
		});
		return content;
	}

}
