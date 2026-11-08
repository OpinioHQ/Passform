package com.opinio.passform.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.ProfileAutofill;
import com.opinio.passform.domain.user.Profile;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ProfileRepository;

// POST /forms/{form_id}/autofill(SPEC 5절 106행, AC4~AC6). 제안만 돌려주고 아무것도 저장하지 않는다.
// 사람이 정한 것(2026-11-08): 마감된 폼은 409 form_closed, 제안은 최신 배포 버전 기준이다.
@Service
public class AutofillService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ProfileRepository profiles;

	private final Clock clock;

	public AutofillService(FormRepository forms, FormVersionRepository versions, ProfileRepository profiles, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ProfileAutofill.Result autofill(UUID userId, String formId, String profileId) {
		Form form = forms.findById(FormService.parseId(formId))
			.filter(found -> !found.isHiddenFromPublic())
			.orElseThrow(AutofillService::notFound);
		if (form.isClosedAt(clock.instant())) {
			throw new DomainException(ErrorCode.FORM_CLOSED);
		}
		Profile profile = profiles.findByIdAndUserId(FormService.parseId(profileId), userId)
			.orElseThrow(AutofillService::notFound);
		Map<String, Object> definition = versions.findById(new FormVersionId(form.getId(), form.getDefinitionVersion()))
			.orElseThrow(() -> new IllegalStateException("배포 버전의 정의가 없다"))
			.getDefinition();
		return ProfileAutofill.suggest(definition, profile.getPersonalInfo());
	}

	private static DomainException notFound() {
		return new DomainException(ErrorCode.NOT_FOUND);
	}

}
