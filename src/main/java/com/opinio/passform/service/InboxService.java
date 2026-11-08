package com.opinio.passform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;
import com.opinio.passform.domain.inbox.SavedFormState;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.ResponseRepository.LatestSubmission;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.UserRepository;

// 내 폼함(SPEC 5절 199~200행, AC17). 본인의 항목만 다룬다. 다른 순서와 탭은 화면이 이 목록으로 계산한다.
@Service
public class InboxService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final UserRepository users;

	private final Clock clock;

	public InboxService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			SavedFormRepository savedForms, UserRepository users, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.users = users;
		this.clock = clock;
	}

	// 배포된 폼만 저장한다. 초안·삭제된 폼·없는 폼은 404다. 처음이면 true(201), 이미 있으면 false(200)이고 saved_at은 그대로다.
	@Transactional
	public boolean save(UUID userId, UUID formId) {
		// 같은 사용자의 저장·초안 첫 저장을 한 번에 하나씩 처리해 항목이 폼당 하나로 남게 한다(DraftService와 같은 잠금).
		users.findByIdForUpdate(userId).orElseThrow(CurrentUserService::noLogin);
		forms.findById(formId)
			.filter(form -> !form.isHiddenFromPublic())
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (savedForms.existsById(new SavedFormId(userId, formId))) {
			return false;
		}
		savedForms.save(SavedForm.saved(userId, formId, clock.instant()));
		return true;
	}

	// 최근 저장순(saved_at 내림차순 → form_id 오름차순). 제목은 마지막 배포 버전의 것이고, closed는 지금 시각으로 계산한다.
	@Transactional(readOnly = true)
	public List<InboxItem> list(UUID userId) {
		List<SavedForm> items = savedForms.findByUserIdOrderBySavedAtDescFormIdAsc(userId);
		if (items.isEmpty()) {
			return List.of();
		}
		Map<UUID, Form> formsById = forms.findAllById(items.stream().map(SavedForm::getFormId).toList())
			.stream()
			.collect(Collectors.toMap(Form::getId, Function.identity()));
		List<FormVersionId> versionIds = formsById.values()
			.stream()
			.filter(form -> form.getDefinitionVersion() != null)
			.map(form -> new FormVersionId(form.getId(), form.getDefinitionVersion()))
			.toList();
		Map<UUID, String> titles = versions.findAllById(versionIds)
			.stream()
			.collect(Collectors.toMap(FormVersion::getFormId, version -> titleOf(version.getDefinition())));
		Set<UUID> drafts = new HashSet<>(responses.findDraftFormIds(userId));
		Map<UUID, Instant> lastSubmitted = responses.findLatestSubmissions(userId)
			.stream()
			.collect(Collectors.toMap(LatestSubmission::getFormId, LatestSubmission::getSubmittedAt));
		Instant now = clock.instant();

		List<InboxItem> result = new ArrayList<>();
		for (SavedForm item : items) {
			Form form = formsById.get(item.getFormId());
			String title = titles.get(item.getFormId());
			// 배포 정의가 없는 항목(폼 삭제 등)은 열 수 없으므로 보이지 않는다. 삭제 뒤 이력 표시는 T12가 정한다.
			if (form == null || title == null) {
				continue;
			}
			result.add(new InboxItem(form.getId(), title, form.getDeadline(), stateOf(item.getState()),
					drafts.contains(form.getId()), item.getSavedAt(), lastSubmitted.get(form.getId()),
					form.isClosedAt(now)));
		}
		return result;
	}

	static String titleOf(Map<String, Object> definition) {
		return definition.get("title") instanceof String title ? title : "";
	}

	private static String stateOf(SavedFormState state) {
		return state == SavedFormState.SUBMITTED ? "submitted" : "saved";
	}

	public record InboxItem(UUID formId, String title, Instant deadline, String state, boolean hasDraft,
			Instant savedAt, Instant submittedAt, boolean closed) {
	}

}
