package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.service.ResponseHistoryService.AttachmentItem;
import com.opinio.passform.service.SubmissionResult.SubmittedAnswer;

// 응답 제출. 문항·분기·형식은 제출 버전의 정의에서, status·deadline·response_limit은 forms의 현재 값에서 읽는다(SPEC 5절 156행).
// 판정 순서: 없는 폼 → 초안 소유 → 버전 → 닫힘 → 동의 → 경로 → 답 검증. 실패하면 아무것도 저장하지 않는다.
@Service
public class SubmissionService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final Clock clock;

	private FormStorageRules storageRules = FormStorageRules.NONE;

	public SubmissionService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			SavedFormRepository savedForms, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.clock = clock;
	}

	// 저장소 없이 판정만 시험하는 테스트에서는 주입되지 않아 NONE이다.
	@Autowired(required = false)
	public void setStorageRules(FormStorageRules storageRules) {
		this.storageRules = storageRules;
	}

	@Transactional
	public SubmissionResult submit(SubmitCommand command) {
		return submit(command, List.of());
	}

	// 파일 없이 판정할 수 있는 것을 S3 업로드 전에 본다. 저장하지 않고 잠그지도 않는다. 제작자 ID를 돌려준다.
	@Transactional(readOnly = true)
	public UUID checkBeforeUpload(SubmitCommand command, Set<String> fileQuestionIds) {
		Form form = forms.findById(command.formId())
			.filter(found -> !found.isHiddenFromPublic())
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		Response draft = command.draftResponseId() == null ? null
				: responses.findById(command.draftResponseId())
					.filter(found -> found.isDraftOf(command.userId(), command.formId()))
					.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		Validated validated = validate(command, form, draft, clock.instant());
		requireFilesOnPath(validated.onPath(), fileQuestionIds);
		return form.getOwnerId();
	}

	// 잠금 안의 최종 판정과 저장. 파일은 이미 S3에 있고, 실패하면 호출자가 지운다.
	@Transactional
	public SubmissionResult submit(SubmitCommand command, List<StoredFile> files) {
		// 폼 행을 잠그므로 같은 폼의 제출은 한 번에 하나씩 인원을 세고 저장한다(AC85). 시각은 잠금을 얻은 뒤에 읽는다.
		Form form = forms.findByIdForUpdate(command.formId())
			.filter(found -> !found.isHiddenFromPublic())
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		// 잠금 순서는 폼 행 → 소유자 행 → 초안 행이다. 초안 저장이 사용자 행 → 초안 행 순서라 뒤집으면 교착이 생긴다.
		long usedBefore = storageRules.lockAndMeasure(form.getOwnerId());
		// 본인의 작성 중 응답만 제출할 수 있다. 남의 것·제출된 것·다른 폼의 것·비로그인은 없는 초안이다(SPEC 5절 188행 404).
		Response draft = command.draftResponseId() == null ? null
				: responses.findByIdForUpdate(command.draftResponseId())
					.filter(found -> found.isDraftOf(command.userId(), command.formId()))
					.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		// 시각은 모든 잠금을 얻은 뒤에 읽는다. 기다리는 사이 마감이 지나면 닫힌 폼이다(AC16).
		Instant now = clock.instant();

		Validated validated = validate(command, form, draft, now);
		requireFilesOnPath(validated.onPath(),
				files.stream().map(StoredFile::questionId).collect(Collectors.toSet()));
		List<Answer> content = validated.content();

		// AI 요약이 꺼진 폼은 보낸 값을 무시하고 false로 저장한다(SPEC 5절 212행).
		boolean aiSummaryAgreed = validated.definition().aiSummaryEnabled()
				&& Boolean.TRUE.equals(command.aiSummaryAgreed());
		Response saved;
		if (draft == null) {
			saved = responses.save(Response.submitted(command.formId(), command.userId(), command.formVersion(), content,
					aiSummaryAgreed, now));
		}
		else {
			// 같은 response_id를 submitted로 바꾼다(AC83). 검증이 실패하면 여기까지 오지 않아 초안은 그대로다.
			draft.submitDraft(content, aiSummaryAgreed, now);
			saved = draft;
		}
		if (form.isResponseLimitReached(validated.submittedCount() + 1)) {
			form.closeByResponseLimit(now);
		}
		recordInInbox(command.userId(), command.formId(), now);
		List<AttachmentItem> attached = storageRules.attachToResponse(command.formId(), saved.getId(), files, now);
		storageRules.requireWithinLimit(form.getOwnerId(), usedBefore);
		return new SubmissionResult(saved.getId(), saved.getFormVersion(), submittedAnswers(content, validated.onPath()),
				attached, now);
	}

	private Validated validate(SubmitCommand command, Form form, Response draft, Instant now) {
		// 옛 버전에서 시작한 초안은 그대로 제출할 수 없다(SPEC 5절 208행, AC83).
		if (!Objects.equals(form.getDefinitionVersion(), command.formVersion())
				|| (draft != null && draft.getFormVersion() != command.formVersion())) {
			throw new DomainException(ErrorCode.FORM_VERSION_CHANGED);
		}
		long submittedCount = form.getResponseLimit() == null ? 0
				: responses.countSubmittedNotDeleted(command.formId());
		if (form.isClosedAt(now) || form.isResponseLimitReached(submittedCount)) {
			throw new DomainException(ErrorCode.FORM_CLOSED);
		}
		if (!Boolean.TRUE.equals(command.consentAgreed())) {
			throw new DomainException(ErrorCode.CONSENT_REQUIRED);
		}

		FormDefinition definition = FormDefinition.from(versions
			.findById(new FormVersionId(command.formId(), command.formVersion()))
			.orElseThrow(() -> new IllegalStateException("배포 버전의 정의가 없다"))
			.getDefinition());
		List<Question> onPath = ResponsePathCalculator.calculate(definition, valuesByQuestion(command.answers())).onPath();
		List<Answer> content = AnswerValidator.validate(onPath, command.answers());
		return new Validated(definition, onPath, content, submittedCount);
	}

	// 파일은 응답 경로 위의 문항에만 연결한다. 없는 문항이거나 분기로 건너뛴 문항이면 형식 오류다.
	static void requireFilesOnPath(List<Question> onPath, Set<String> fileQuestionIds) {
		Set<String> allowed = onPath.stream().map(Question::questionId).collect(Collectors.toSet());
		if (!allowed.containsAll(fileQuestionIds)) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
	}

	private record Validated(FormDefinition definition, List<Question> onPath, List<Answer> content,
			long submittedCount) {
	}

	// 로그인 사용자의 제출은 같은 트랜잭션에서 내 폼함 항목을 submitted로 만든다. 저장하지 않았던 폼이면 지금 넣는다(AC17).
	// 항목은 폼당 하나이고, 이미 있으면 saved_at을 바꾸지 않는다.
	private void recordInInbox(UUID userId, UUID formId, Instant now) {
		if (userId == null) {
			return;
		}
		savedForms.findById(new SavedFormId(userId, formId))
			.ifPresentOrElse(SavedForm::markSubmitted, () -> savedForms.save(SavedForm.submitted(userId, formId, now)));
	}

	// 경로 계산용이다. 같은 문항의 답이 여럿이면 첫 답을 쓰고, 그 문항이 경로 위면 검증에서 거부된다.
	static Map<String, List<String>> valuesByQuestion(List<Answer> answers) {
		return answers.stream()
			.filter(answer -> answer.questionId() != null)
			.collect(Collectors.toMap(Answer::questionId, Answer::values, (first, second) -> first));
	}

	static List<SubmittedAnswer> submittedAnswers(List<Answer> content, List<Question> onPath) {
		Map<String, String> notes = onPath.stream()
			.collect(Collectors.toMap(Question::questionId, Question::questionNote));
		return content.stream()
			.map(answer -> new SubmittedAnswer(answer.questionId(), notes.get(answer.questionId()), answer.value()))
			.toList();
	}

}
