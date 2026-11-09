package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.PublicFormService.PublicFormView;

// 로그인 응답자의 계정 초안(SPEC 5절 189~191행, 205~208행, AC82·AC83). 본인 것만 다룬다.
// 초안에는 필수·형식·분기 같은 제출 검증을 하지 않고, 동의나 제출로 처리하지 않는다.
// 초안은 in_progress라 결과·분석·내보내기가 읽는 ResponseRepository.findSubmittedNotDeletedByFormId에 나타나지 않는다.
@Service
public class DraftService {

	private final FormRepository forms;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final UserRepository users;

	private final PublicFormService publicForms;

	private final FormStorageRules storageRules;

	private final Clock clock;

	public DraftService(FormRepository forms, ResponseRepository responses, SavedFormRepository savedForms,
			UserRepository users, PublicFormService publicForms, FormStorageRules storageRules, Clock clock) {
		this.storageRules = storageRules;
		this.forms = forms;
		this.responses = responses;
		this.savedForms = savedForms;
		this.users = users;
		this.publicForms = publicForms;
		this.clock = clock;
	}

	// 초안과 작성 시작 버전의 정의. 폼이 닫혔어도 초안은 보존하므로 돌려준다(SPEC 5절 208행).
	@Transactional(readOnly = true)
	public DraftView find(UUID userId, UUID formId) {
		Form form = visibleForm(formId);
		Response draft = responses.findDraft(userId, formId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		return new DraftView(draft.getId(), draft.getFormVersion(), publicForms.viewOfVersion(form, draft.getFormVersion()),
				draft.getContent(), draft.getSavedAt());
	}

	// 첫 저장은 초안을 만들고 내 폼함에 saved로 한 번 넣는다(AC17). 이후에는 같은 초안의 답을 바꾼다.
	// 최신 배포 버전이 아니거나 옛 버전 초안이면 409 form_version_changed다. 옛 초안은 지우고 새로 시작해야 한다.
	@Transactional
	public SaveResult save(UUID userId, UUID formId, int formVersion, List<Answer> answers) {
		// 같은 사용자의 요청을 한 번에 하나씩 처리한다. 동시 첫 저장에서도 초안은 한 건이고 유일 제약 위반(500)이 나지 않는다.
		users.findByIdForUpdate(userId).orElseThrow(CurrentUserService::noLogin);
		// 계정 초안의 답은 응답자 자신의 사용량이다(SPEC 5절 172행).
		long usedBefore = storageRules.lockAndMeasure(userId);
		Form form = visibleForm(formId);
		Instant now = clock.instant();
		// 제출과 같은 순서로 본다: 버전 → 닫힘.
		if (!Objects.equals(form.getDefinitionVersion(), formVersion)) {
			throw new DomainException(ErrorCode.FORM_VERSION_CHANGED);
		}
		if (form.isClosedAt(now)) {
			throw new DomainException(ErrorCode.FORM_CLOSED);
		}
		Response draft = responses.findDraftForUpdate(userId, formId).orElse(null);
		boolean created = draft == null;
		if (created) {
			draft = responses.save(Response.draft(formId, userId, formVersion, answers, now));
		}
		else if (draft.getFormVersion() != formVersion) {
			throw new DomainException(ErrorCode.FORM_VERSION_CHANGED);
		}
		else {
			draft.replaceDraftAnswers(answers, now);
		}
		if (!savedForms.existsById(new SavedFormId(userId, formId))) {
			savedForms.save(SavedForm.saved(userId, formId, now));
		}
		storageRules.requireWithinLimit(userId, usedBefore);
		return new SaveResult(draft.getId(), now, created);
	}

	// 본인의 작성 중 답만 지운다. 없어도 204다(SPEC 5절 191행). 내 폼함 항목과 제출 응답은 그대로 둔다.
	@Transactional
	public void delete(UUID userId, UUID formId) {
		responses.findDraftForUpdate(userId, formId).ifPresent(responses::delete);
	}

	// 초안·삭제된 폼은 없는 폼이다(공개 조회와 같다).
	private Form visibleForm(UUID formId) {
		return forms.findById(formId)
			.filter(form -> !form.isHiddenFromPublic())
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
	}

	public record DraftView(UUID responseId, int formVersion, PublicFormView formDefinition, List<Answer> answers,
			Instant savedAt) {
	}

	public record SaveResult(UUID responseId, Instant savedAt, boolean created) {
	}

}
