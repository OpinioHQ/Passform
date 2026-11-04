package com.opinio.passform.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.opinio.passform.demo.domain.ConflictException;
import com.opinio.passform.demo.domain.InvalidRequestException;
import com.opinio.passform.demo.domain.NotFoundException;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormStatus;
import com.opinio.passform.demo.domain.form.FormVersion;
import com.opinio.passform.demo.domain.response.Response;
import com.opinio.passform.demo.domain.response.SavedForm;
import com.opinio.passform.demo.repository.FormRepository;
import com.opinio.passform.demo.repository.FormVersionRepository;
import com.opinio.passform.demo.repository.ResponseRepository;
import com.opinio.passform.demo.repository.SavedFormRepository;

class SubmissionServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");

	private static final String DEFINITION = """
			{"title":"t","sections":[{"section_id":"s","title":"s","questions":[
			 {"question_id":"q1","question_note":"이름","question_type":"short_answer","required":true}]}]}""";

	private final FormRepository forms = mock(FormRepository.class);

	private final FormVersionRepository versions = mock(FormVersionRepository.class);

	private final ResponseRepository responses = mock(ResponseRepository.class);

	private final SavedFormRepository savedForms = mock(SavedFormRepository.class);

	private final SubmissionService service = new SubmissionService(forms, versions, responses, savedForms,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private static final SubmissionService.SubmitCommand VALID = new SubmissionService.SubmitCommand(2,
			Map.of("q1", List.of("김")), true, true);

	@BeforeEach
	void 버전_정의를_준비한다() {
		when(versions.findByFormIdAndVersion("f", 2)).thenReturn(Optional.of(new FormVersion("f", 2, DEFINITION)));
	}

	private Form form(FormStatus status, Instant deadline, Integer limit, boolean aiSummary) {
		Form form = new Form("f", "owner", "f", status, "t", 2, deadline, limit, false, false, aiSummary, "c", "all_questions", NOW);
		when(forms.findByIdForUpdate("f")).thenReturn(Optional.of(form));
		return form;
	}

	private void assertConflict(SubmissionService.SubmitCommand command, String error) {
		assertThatThrownBy(() -> service.submit("f", null, command)).isInstanceOfSatisfying(ConflictException.class,
				exception -> assertThat(exception.getMessage()).isEqualTo(error));
		verify(responses, never()).save(any());
	}

	@Test
	void 정상_제출은_제출_버전과_답을_저장해_돌려준다() {
		form(FormStatus.OPEN, null, null, true);
		SubmissionService.SubmittedView view = service.submit("f", null, VALID);
		ArgumentCaptor<Response> saved = ArgumentCaptor.forClass(Response.class);
		verify(responses).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(ResponseStatus.SUBMITTED);
		assertThat(saved.getValue().getFormVersion()).isEqualTo(2);
		assertThat(saved.getValue().isAiSummaryAgreed()).isTrue();
		assertThat(view.status()).isEqualTo("submitted");
		assertThat(view.submittedAt()).isEqualTo(NOW);
		assertThat(view.updatedAt()).isNull();
		assertThat(view.content()).singleElement().satisfies(answer -> assertThat(answer.value()).isEqualTo("김"));
	}

	@Test
	void AC41_마감이_지나거나_닫힌_폼은_받지_않는다() {
		form(FormStatus.OPEN, NOW.minusSeconds(60), null, true);
		assertConflict(VALID, "form_closed");
		form(FormStatus.CLOSED, null, null, true);
		assertConflict(VALID, "form_closed");
	}

	@Test
	void AC76_옛_버전으로_제출하면_저장하지_않는다() {
		form(FormStatus.OPEN, null, null, true);
		assertConflict(new SubmissionService.SubmitCommand(1, Map.of("q1", List.of("김")), true, true), "form_version_changed");
	}

	@Test
	void 동의가_없으면_저장하지_않는다() {
		form(FormStatus.OPEN, null, null, true);
		assertThatThrownBy(() -> service.submit("f", null, new SubmissionService.SubmitCommand(2, Map.of("q1", List.of("김")), false, true)))
			.isInstanceOfSatisfying(InvalidRequestException.class,
					exception -> assertThat(exception.getMessage()).isEqualTo("consent_required"));
		verify(responses, never()).save(any());
	}

	@Test
	void AC43_필수_문항을_비우면_문항_id를_돌려주고_저장하지_않는다() {
		form(FormStatus.OPEN, null, null, true);
		assertThatThrownBy(() -> service.submit("f", null, new SubmissionService.SubmitCommand(2, Map.of(), true, false)))
			.isInstanceOfSatisfying(InvalidRequestException.class,
					exception -> assertThat(exception.getQuestionIds()).containsExactly("q1"));
		verify(responses, never()).save(any());
	}

	@Test
	void 초안_폼과_없는_폼은_404다() {
		form(FormStatus.DRAFT, null, null, true);
		assertThatThrownBy(() -> service.submit("f", null, VALID)).isInstanceOf(NotFoundException.class);
		when(forms.findByIdForUpdate("없음")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.submit("없음", null, VALID)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void AI_요약이_꺼진_폼은_보낸_동의를_무시한다() {
		form(FormStatus.OPEN, null, null, false);
		service.submit("f", null, VALID);
		ArgumentCaptor<Response> saved = ArgumentCaptor.forClass(Response.class);
		verify(responses).save(saved.capture());
		assertThat(saved.getValue().isAiSummaryAgreed()).isFalse();
	}

	@Test
	void AC17_로그인_제출은_내_폼함에_submitted로_한_번만_넣는다() {
		form(FormStatus.OPEN, null, null, true);
		when(savedForms.findById(SavedForm.idOf("user", "f"))).thenReturn(Optional.empty());
		service.submit("f", "user", VALID);
		ArgumentCaptor<SavedForm> saved = ArgumentCaptor.forClass(SavedForm.class);
		verify(savedForms).save(saved.capture());
		assertThat(saved.getValue().getState()).isEqualTo(SavedForm.SUBMITTED);

		SavedForm existing = new SavedForm("user", "f", SavedForm.SAVED);
		when(savedForms.findById(SavedForm.idOf("user", "f"))).thenReturn(Optional.of(existing));
		service.submit("f", "user", VALID);
		assertThat(existing.getState()).isEqualTo(SavedForm.SUBMITTED);
	}

	@Test
	void 비로그인_제출은_내_폼함에_남지_않는다() {
		form(FormStatus.OPEN, null, null, true);
		service.submit("f", null, VALID);
		verify(savedForms, never()).save(any());
	}

	@Test
	void 선착순_인원에_도달하면_같은_처리_안에서_닫는다() {
		Form form = form(FormStatus.OPEN, null, 3, true);
		when(responses.countByFormIdAndStatus("f", ResponseStatus.SUBMITTED)).thenReturn(3L);
		service.submit("f", null, VALID);
		assertThat(form.getStatus()).isEqualTo(FormStatus.CLOSED);
	}

}
