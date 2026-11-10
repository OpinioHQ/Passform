package com.opinio.passform.service;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.service.ResponseHistoryService.AttachmentItem;

// AC16: 마감은 모든 잠금을 얻은 뒤의 시각으로 판정한다. 소유자 잠금을 기다리는 사이 마감이 지나면 제출하지 않는다(#107 리뷰 5번).
class SubmissionLockClockTest {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant DEADLINE = NOW.plusSeconds(60);

	private static final UUID FORM_ID = UUID.randomUUID();

	private static final class MovableClock extends Clock {

		private Instant now = NOW;

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@Test
	void 소유자_잠금을_기다리는_사이_마감이_지나면_form_closed이고_저장하지_않는다() {
		Map<String, Object> simple = definition("one_question", List.of(section("s1", text("q1", true))));
		Form form = new Form(UUID.randomUUID(), simple, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", FORM_ID);
		ReflectionTestUtils.setField(form, "status", FormStatus.OPEN);
		ReflectionTestUtils.setField(form, "definitionVersion", 1);
		ReflectionTestUtils.setField(form, "deadline", DEADLINE);
		FormRepository forms = mock(FormRepository.class);
		when(forms.findByIdForUpdate(FORM_ID)).thenReturn(Optional.of(form));
		FormVersionRepository versions = mock(FormVersionRepository.class);
		when(versions.findById(any(FormVersionId.class))).thenReturn(Optional.of(new FormVersion(FORM_ID, 1, simple, Instant.EPOCH)));
		ResponseRepository responses = mock(ResponseRepository.class);
		MovableClock clock = new MovableClock();
		SubmissionService service = new SubmissionService(forms, versions, responses, mock(SavedFormRepository.class), clock);
		service.setStorageRules(new FormStorageRules() {

			// 잠금을 기다리는 동안 마감이 지난다.
			@Override
			public long lockAndMeasure(UUID ownerId) {
				clock.now = DEADLINE.plusSeconds(1);
				return 0;
			}

			@Override
			public void requireWithinLimit(UUID ownerId, long usedBefore) {
			}

			@Override
			public void releaseUnreferenced(UUID formId, Set<String> attachmentIds) {
			}

			@Override
			public Map<String, String> attachCopies(UUID formId, List<CopiedFile> files, Instant now) {
				return Map.of();
			}

			@Override
			public List<AttachmentItem> attachToResponse(UUID formId, UUID responseId, List<StoredFile> files, Instant now) {
				return List.of();
			}

		});

		assertThatThrownBy(() -> service.submit(new SubmitCommand(FORM_ID, null, 1, List.of(new Answer("q1", "값")), true, null)))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORM_CLOSED));
		org.mockito.Mockito.verify(responses, org.mockito.Mockito.never()).save(any(Response.class));
	}

}
