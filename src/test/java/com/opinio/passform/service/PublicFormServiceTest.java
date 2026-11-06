package com.opinio.passform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.template.ConsentTemplate;
import com.opinio.passform.repository.ConsentTemplateRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.support.SampleForms;

// 마감 판정은 주입한 Clock으로 한다. 경계는 고정 시계로 본다(AC41, AC16).
class PublicFormServiceTest {

	private static final Instant DEADLINE = Instant.parse("2026-11-30T14:59:00Z");

	private final FormRepository forms = mock(FormRepository.class);

	private final FormVersionRepository versions = mock(FormVersionRepository.class);

	private final ConsentTemplateRepository consentTemplates = mock(ConsentTemplateRepository.class);

	private PublicFormService serviceAt(Instant now) {
		return new PublicFormService(forms, versions, consentTemplates, Clock.fixed(now, ZoneOffset.UTC));
	}

	private void givenOpenFormWithDeadline() {
		UUID formId = UUID.randomUUID();
		Map<String, Object> definition = SampleForms.unbranched();
		Form form = new Form(UUID.randomUUID(), definition, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", formId);
		form.replaceDefinition(definition, DEADLINE, false, false, null, Instant.EPOCH);
		form.moveTo(FormStatus.OPEN);
		form.recordPublishedVersion(1, "slug", null);
		when(forms.findBySlug("slug")).thenReturn(Optional.of(form));
		when(versions.findById(any(FormVersionId.class)))
			.thenReturn(Optional.of(new FormVersion(formId, 1, definition, Instant.EPOCH)));
		when(consentTemplates.findById("consent_default"))
			.thenReturn(Optional.of(new ConsentTemplate("consent_default", "목적", "보유")));
	}

	@Test
	void 마감_1초_전에는_조회된다() {
		givenOpenFormWithDeadline();

		assertThat(serviceAt(DEADLINE.minusSeconds(1)).findBySlug("slug").deadline()).isEqualTo(DEADLINE);
	}

	@Test
	void 마감_시각에_정확히_닫힌다() {
		givenOpenFormWithDeadline();

		assertThatThrownBy(() -> serviceAt(DEADLINE).findBySlug("slug")).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.FORM_CLOSED);
	}

}
