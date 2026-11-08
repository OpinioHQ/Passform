package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.AnswerValidator;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.FormDefinition.Question;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.ResponsePathCalculator;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.response.ResponseChangePolicy;
import com.opinio.passform.domain.response.ResponseChangePolicy.Change;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.service.ResponseHistoryService.AttachmentItem;
import com.opinio.passform.service.SubmissionResult.SubmittedAnswer;

// 제출 후 수정·취소(SPEC 5절 194~195행, AC16, AC17, AC74). 본인의 submitted 응답만 다룬다. 남의 것·없는 것·작성 중인 것은 같은 404다(AC2).
// 제출과 같은 폼 행 잠금 아래에서 판정한다.
@Service
public class ResponseChangeService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final AttachmentRepository attachments;

	private final Clock clock;

	public ResponseChangeService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			SavedFormRepository savedForms, AttachmentRepository attachments, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.attachments = attachments;
		this.clock = clock;
	}

	// 같은 Response를 덮어쓴다. response_id·form_version·submitted_at·ai_summary_agreed는 그대로다(AC74, SPEC 5절 215·223행).
	// 검증은 제출 경로의 계산을 그대로 쓰고 정의만 그 응답의 제출 버전에서 읽는다.
	@Transactional
	public EditedResponse edit(EditCommand command) {
		Target target = lockOwned(command.userId(), command.responseId());
		Instant now = clock.instant();
		requireAllowed(Change.EDIT, target, now);
		Response response = target.response();
		FormDefinition definition = FormDefinition.from(versions
			.findById(new FormVersionId(response.getFormId(), response.getFormVersion()))
			.orElseThrow(() -> new IllegalStateException("제출 버전의 정의가 없다"))
			.getDefinition());
		List<Question> onPath = ResponsePathCalculator
			.calculate(definition, SubmissionService.valuesByQuestion(command.answers()))
			.onPath();
		List<Answer> content = AnswerValidator.validate(onPath, command.answers());
		response.replaceSubmittedAnswers(content, now);
		return new EditedResponse(response.getId(), response.getFormVersion(), SubmissionService.submittedAnswers(content, onPath),
				attachments.findByResponseIdOrderByCreatedAtAscIdAsc(response.getId()).stream().map(AttachmentItem::of).toList(),
				response.getSubmittedAt(), response.getUpdatedAt());
	}

	// 응답 행을 지운다. abandoned는 다른 용도로 예약되어 있다(SPEC 5절 86행).
	// 사람이 정한 것(2026-11-08): 첨부는 연결만 끊고 행을 남긴다. 원본 정리는 T12가 s3_key로 한다.
	@Transactional
	public void cancel(UUID userId, UUID responseId) {
		Target target = lockOwned(userId, responseId);
		requireAllowed(Change.CANCEL, target, clock.instant());
		Response response = target.response();
		attachments.detachFromResponse(response.getId());
		responses.delete(response);
		// 제작자가 지운 이력도 취소되지 않은 제출 기록이다(AC17). 쿼리 전에 위 삭제가 반영된다.
		if (!responses.existsSubmittedByUser(userId, response.getFormId())) {
			savedForms.findById(new SavedFormId(userId, response.getFormId())).ifPresent(SavedForm::markSaved);
		}
	}

	// 폼 행을 먼저 잠근다. 응답을 먼저 잠그면 같은 폼의 제출과 서로 기다릴 수 있다.
	// 잠금을 기다리는 사이 취소되었으면 없는 응답과 같은 404다.
	private Target lockOwned(UUID userId, UUID responseId) {
		UUID formId = responses.findFormIdOfSubmitted(responseId, userId)
			.orElseThrow(ResponseChangeService::notFound);
		Form form = forms.findByIdForUpdate(formId).orElse(null);
		Response response = responses.findByIdForUpdate(responseId)
			.filter(found -> found.getStatus() == ResponseStatus.SUBMITTED)
			.orElseThrow(ResponseChangeService::notFound);
		return new Target(form, response);
	}

	private static void requireAllowed(Change change, Target target, Instant now) {
		ResponseChangePolicy.blockedBy(change, target.form(), target.response(), now).ifPresent(code -> {
			throw new DomainException(code);
		});
	}

	private static DomainException notFound() {
		return new DomainException(ErrorCode.NOT_FOUND);
	}

	private record Target(Form form, Response response) {
	}

	public record EditCommand(UUID userId, UUID responseId, List<Answer> answers) {
	}

	public record EditedResponse(UUID responseId, int formVersion, List<SubmittedAnswer> content,
			List<AttachmentItem> attachments, Instant submittedAt, Instant updatedAt) {
	}

}
