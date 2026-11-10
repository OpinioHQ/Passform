package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.attachment.Attachment;
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

	private final StorageQuotaService quota;

	private final Clock clock;

	public ResponseChangeService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			SavedFormRepository savedForms, AttachmentRepository attachments, StorageQuotaService quota, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.attachments = attachments;
		this.quota = quota;
		this.clock = clock;
	}

	// 파일 없이 판정할 수 있는 것을 S3 업로드 전에 본다. 잠그지도 저장하지도 않는다.
	@Transactional(readOnly = true)
	public EditPrecheck checkBeforeUpload(EditCommand command, Set<String> fileQuestionIds) {
		Response response = responses.findById(command.responseId())
			.filter(found -> found.getStatus() == ResponseStatus.SUBMITTED && command.userId().equals(found.getUserId()))
			.orElseThrow(ResponseChangeService::notFound);
		Form form = forms.findById(response.getFormId()).orElse(null);
		requireAllowed(Change.EDIT, new Target(form, response), clock.instant());
		SubmissionService.requireFilesOnPath(validate(response, command.answers()).onPath(), fileQuestionIds);
		FileChange change = keptFiles(response.getId(), command.keepAttachmentIds());
		Map<String, Long> keptBytes = change.kept()
			.stream()
			.collect(Collectors.groupingBy(Attachment::getQuestionId, Collectors.summingLong(Attachment::getSizeBytes)));
		long freedBytes = change.removed().stream().mapToLong(Attachment::getSizeBytes).sum();
		return new EditPrecheck(response.getFormId(), form.getOwnerId(), keptBytes, freedBytes);
	}

	// 같은 Response를 덮어쓴다. response_id·form_version·submitted_at·ai_summary_agreed는 그대로다(AC74, SPEC 5절 215·223행).
	// 검증은 제출 경로의 계산을 그대로 쓰고 정의만 그 응답의 제출 버전에서 읽는다.
	// 첨부 목록을 생략하면 기존 파일을 유지하고, 보내면 그 목록과 새 파일로 교체한다(SPEC 5절 222행). 파일은 이미 S3에 있다.
	@Transactional
	public EditedResponse edit(EditCommand command) {
		Target target = lockOwned(command.userId(), command.responseId());
		Instant now = clock.instant();
		requireAllowed(Change.EDIT, target, now);
		Response response = target.response();
		long usedBefore = target.usedBefore();
		Validated validated = validate(response, command.answers());
		SubmissionService.requireFilesOnPath(validated.onPath(),
				command.newFiles().stream().map(StoredFile::questionId).collect(Collectors.toSet()));
		FileChange change = keptFiles(response.getId(), command.keepAttachmentIds());
		ResponseFileLimits.requireWithinQuestionLimit(change.kept(), command.newFiles());

		response.replaceSubmittedAnswers(validated.content(), now);
		quota.releaseResponseFiles(response.getFormId(), change.removed());
		quota.attachToResponse(response.getFormId(), response.getId(), command.newFiles(), now);
		quota.requireWithinLimit(target.form().getOwnerId(), usedBefore);
		return new EditedResponse(response.getId(), response.getFormVersion(),
				SubmissionService.submittedAnswers(validated.content(), validated.onPath()),
				attachments.findByResponseIdOrderByCreatedAtAscIdAsc(response.getId()).stream().map(AttachmentItem::of).toList(),
				response.getSubmittedAt(), response.getUpdatedAt());
	}

	private Validated validate(Response response, List<Answer> answers) {
		FormDefinition definition = FormDefinition.from(versions
			.findById(new FormVersionId(response.getFormId(), response.getFormVersion()))
			.orElseThrow(() -> new IllegalStateException("제출 버전의 정의가 없다"))
			.getDefinition());
		List<Question> onPath = ResponsePathCalculator
			.calculate(definition, SubmissionService.valuesByQuestion(answers))
			.onPath();
		return new Validated(onPath, AnswerValidator.validate(onPath, answers));
	}

	// keep이 null이면 모두 유지한다. 이 응답의 사용 가능한 파일이 아닌 ID가 있으면 형식 오류다.
	private FileChange keptFiles(UUID responseId, List<String> keep) {
		List<Attachment> current = attachments.findByResponseIdOrderByCreatedAtAscIdAsc(responseId);
		if (keep == null) {
			return new FileChange(current.stream().filter(Attachment::isAvailable).toList(), List.of());
		}
		Set<String> keepIds = Set.copyOf(keep);
		Set<String> known = current.stream()
			.filter(Attachment::isAvailable)
			.map(file -> file.getId().toString())
			.collect(Collectors.toSet());
		if (!known.containsAll(keepIds)) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		List<Attachment> kept = current.stream().filter(file -> keepIds.contains(file.getId().toString())).toList();
		List<Attachment> removed = current.stream()
			.filter(file -> file.isAvailable() && !keepIds.contains(file.getId().toString()))
			.toList();
		return new FileChange(kept, removed);
	}

	// 응답 행을 지운다. abandoned는 다른 용도로 예약되어 있다(SPEC 5절 86행).
	// 첨부 행도 지우고 커밋 뒤 원본을 지운다. 취소한 파일은 받을 수 없고 어느 사용량에도 잡히지 않는다(SPEC 5절 222행).
	@Transactional
	public void cancel(UUID userId, UUID responseId) {
		Target target = lockOwned(userId, responseId);
		requireAllowed(Change.CANCEL, target, clock.instant());
		Response response = target.response();
		quota.releaseResponseFiles(response.getFormId(),
				attachments.findByResponseIdOrderByCreatedAtAscIdAsc(response.getId()));
		responses.delete(response);
		// 제작자가 지운 이력도 취소되지 않은 제출 기록이다(AC17). 쿼리 전에 위 삭제가 반영된다.
		if (!responses.existsSubmittedByUser(userId, response.getFormId())) {
			savedForms.findById(new SavedFormId(userId, response.getFormId())).ifPresent(SavedForm::markSaved);
		}
	}

	// 폼 행 → 소유자 행 → 응답 행 순서로 잠근다. 응답을 먼저 잠그면 같은 폼의 제출과 서로 기다릴 수 있다.
	// 잠금을 기다리는 사이 취소되었으면 없는 응답과 같은 404다.
	private Target lockOwned(UUID userId, UUID responseId) {
		UUID formId = responses.findFormIdOfSubmitted(responseId, userId)
			.orElseThrow(ResponseChangeService::notFound);
		Form form = forms.findByIdForUpdate(formId).orElse(null);
		long usedBefore = form == null ? 0 : quota.lockAndMeasure(form.getOwnerId());
		Response response = responses.findByIdForUpdate(responseId)
			.filter(found -> found.getStatus() == ResponseStatus.SUBMITTED)
			.orElseThrow(ResponseChangeService::notFound);
		return new Target(form, response, usedBefore);
	}

	private static void requireAllowed(Change change, Target target, Instant now) {
		ResponseChangePolicy.blockedBy(change, target.form(), target.response(), now).ifPresent(code -> {
			throw new DomainException(code);
		});
	}

	private static DomainException notFound() {
		return new DomainException(ErrorCode.NOT_FOUND);
	}

	private record Target(Form form, Response response, long usedBefore) {

		Target(Form form, Response response) {
			this(form, response, 0);
		}

	}

	private record Validated(List<Question> onPath, List<Answer> content) {
	}

	private record FileChange(List<Attachment> kept, List<Attachment> removed) {
	}

	// keepAttachmentIds가 null이면 기존 파일을 모두 유지한다.
	public record EditCommand(UUID userId, UUID responseId, List<Answer> answers, List<String> keepAttachmentIds,
			List<StoredFile> newFiles) {

		public EditCommand(UUID userId, UUID responseId, List<Answer> answers) {
			this(userId, responseId, answers, null, List.of());
		}

	}

	// freedBytes는 교체로 빠지는 파일의 합계다. 용량 사전 검사에서 뺀다.
	public record EditPrecheck(UUID formId, UUID ownerId, Map<String, Long> keptBytesByQuestion, long freedBytes) {
	}

	public record EditedResponse(UUID responseId, int formVersion, List<SubmittedAnswer> content,
			List<AttachmentItem> attachments, Instant submittedAt, Instant updatedAt) {
	}

}
