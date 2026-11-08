package com.opinio.passform.service;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.formatted;
import static com.opinio.passform.support.FormDefinitions.question;
import static com.opinio.passform.support.FormDefinitions.rule;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static com.opinio.passform.support.FormDefinitions.toSection;
import static com.opinio.passform.support.FormDefinitions.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

class SubmissionServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	private static final UUID FORM_ID = UUID.randomUUID();

	private static final List<String> YES_NO = List.of("예", "아니오");

	private static final Map<String, Object> SIMPLE = definition("one_question", List.of(section("s1", text("q1", true))));

	private final List<Response> saved = new ArrayList<>();

	private Form form;

	private FormVersion version;

	private SubmissionService service(Instant now) {
		FormRepository forms = mock(FormRepository.class);
		Mockito.when(forms.findByIdForUpdate(any(UUID.class)))
			.thenAnswer(call -> FORM_ID.equals(call.getArgument(0)) ? Optional.ofNullable(form) : Optional.empty());
		FormVersionRepository versions = mock(FormVersionRepository.class);
		Mockito.when(versions.findById(any(FormVersionId.class)))
			.thenAnswer(call -> call.<FormVersionId>getArgument(0).version() == version.getVersion() ? Optional.of(version)
					: Optional.empty());
		ResponseRepository responses = mock(ResponseRepository.class);
		Mockito.when(responses.countSubmittedNotDeleted(FORM_ID)).thenAnswer(call -> (long) saved.size());
		Mockito.when(responses.save(any(Response.class))).thenAnswer(call -> {
			Response response = call.getArgument(0);
			ReflectionTestUtils.setField(response, "id", UUID.randomUUID());
			saved.add(response);
			return response;
		});
		return new SubmissionService(forms, versions, responses, mock(SavedFormRepository.class),
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private void givenForm(FormStatus status, int currentVersion, Instant deadline, Integer limit,
			Map<String, Object> definition) {
		form = new Form(UUID.randomUUID(), definition, Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", FORM_ID);
		ReflectionTestUtils.setField(form, "status", status);
		ReflectionTestUtils.setField(form, "definitionVersion", currentVersion);
		ReflectionTestUtils.setField(form, "deadline", deadline);
		ReflectionTestUtils.setField(form, "responseLimit", limit);
		version = new FormVersion(FORM_ID, currentVersion, definition, Instant.EPOCH);
	}

	private static SubmitCommand command(UUID userId, int version, Boolean consent, Boolean aiSummary, Answer... answers) {
		return new SubmitCommand(FORM_ID, userId, version, List.of(answers), consent, aiSummary);
	}

	private static SubmitCommand valid() {
		return command(null, 1, true, null, new Answer("q1", "값"));
	}

	private void assertRejected(Instant now, SubmitCommand command, ErrorCode code) {
		int before = saved.size();
		assertThatThrownBy(() -> service(now).submit(command))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
		assertThat(saved).hasSize(before);
	}

	@Test
	void AC14_동의를_생략하거나_false면_consent_required고_저장하지_않는다() {
		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);
		assertRejected(NOW, command(null, 1, null, null, new Answer("q1", "값")), ErrorCode.CONSENT_REQUIRED);
		assertRejected(NOW, command(null, 1, false, null, new Answer("q1", "값")), ErrorCode.CONSENT_REQUIRED);
	}

	@Test
	void AC16_now와_deadline이_같으면_닫혔고_1분_전이면_받는다() {
		givenForm(FormStatus.OPEN, 1, NOW, null, SIMPLE);
		assertRejected(NOW, valid(), ErrorCode.FORM_CLOSED);
		assertRejected(NOW.plusSeconds(60), valid(), ErrorCode.FORM_CLOSED);

		service(NOW.minusSeconds(60)).submit(valid());
		assertThat(saved).hasSize(1);
	}

	@Test
	void AC16_closed_폼은_거부하고_deadline_없는_open_폼은_받는다() {
		givenForm(FormStatus.CLOSED, 1, null, null, SIMPLE);
		assertRejected(NOW, valid(), ErrorCode.FORM_CLOSED);

		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);
		service(NOW).submit(valid());
		assertThat(saved).hasSize(1);
	}

	@Test
	void 없는_폼_삭제된_폼_초안_폼은_not_found다() {
		form = null;
		version = new FormVersion(FORM_ID, 1, SIMPLE, Instant.EPOCH);
		assertRejected(NOW, valid(), ErrorCode.NOT_FOUND);

		givenForm(FormStatus.DRAFT, 1, null, null, SIMPLE);
		assertRejected(NOW, valid(), ErrorCode.NOT_FOUND);

		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);
		ReflectionTestUtils.setField(form, "deletedAt", NOW);
		assertRejected(NOW, valid(), ErrorCode.NOT_FOUND);
	}

	@Test
	void AC76_옛_버전은_거부하고_최신_버전은_받는다() {
		givenForm(FormStatus.OPEN, 2, null, null, SIMPLE);
		assertRejected(NOW, command(null, 1, true, null, new Answer("q1", "값")), ErrorCode.FORM_VERSION_CHANGED);

		SubmissionResult result = service(NOW).submit(command(null, 2, true, null, new Answer("q1", "값")));
		assertThat(result.formVersion()).isEqualTo(2);
		assertThat(saved.get(0).getFormVersion()).isEqualTo(2);
	}

	@Test
	void AC85_N번째_제출에_closed가_되고_그_뒤는_form_closed다() {
		givenForm(FormStatus.OPEN, 1, null, 2, SIMPLE);

		service(NOW).submit(valid());
		assertThat(form.getStatus()).isEqualTo(FormStatus.OPEN);
		service(NOW).submit(valid());
		assertThat(form.getStatus()).isEqualTo(FormStatus.CLOSED);
		assertThat(form.getUpdatedAt()).isEqualTo(NOW);

		assertRejected(NOW, valid(), ErrorCode.FORM_CLOSED);
		assertThat(saved).hasSize(2);
	}

	@Test
	void AC85_실패한_제출은_인원을_채우지_않고_폼을_닫지_않는다() {
		givenForm(FormStatus.OPEN, 1, null, 1, SIMPLE);
		assertRejected(NOW, command(null, 1, true, null), ErrorCode.REQUIRED_MISSING);
		assertThat(form.getStatus()).isEqualTo(FormStatus.OPEN);
	}

	@Test
	void AC85_response_limit이_null이면_제한이_없다() {
		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);
		for (int i = 0; i < 5; i++) {
			service(NOW).submit(valid());
		}
		assertThat(saved).hasSize(5);
		assertThat(form.getStatus()).isEqualTo(FormStatus.OPEN);
	}

	@Test
	void AC51_같은_사용자의_반복_제출과_비로그인_반복_제출을_각각_저장한다() {
		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);
		UUID user = UUID.randomUUID();

		UUID first = service(NOW).submit(command(user, 1, true, null, new Answer("q1", "값"))).responseId();
		UUID second = service(NOW).submit(command(user, 1, true, null, new Answer("q1", "값"))).responseId();
		service(NOW).submit(valid());
		service(NOW).submit(valid());

		assertThat(first).isNotEqualTo(second);
		assertThat(saved).extracting(Response::getUserId).containsExactly(user, user, null, null);
	}

	@Test
	void AC91_켠_폼은_보낸_동의를_저장하고_없으면_false다() {
		givenForm(FormStatus.OPEN, 1, null, null,
				definition("one_question", true, List.of(section("s1", text("q1", true)))));

		service(NOW).submit(command(null, 1, true, true, new Answer("q1", "값")));
		service(NOW).submit(command(null, 1, true, false, new Answer("q1", "값")));
		service(NOW).submit(command(null, 1, true, null, new Answer("q1", "값")));

		assertThat(saved).extracting(Response::isAiSummaryAgreed).containsExactly(true, false, false);
	}

	@Test
	void AC91_꺼진_폼은_true를_보내도_false로_저장한다() {
		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);

		service(NOW).submit(command(null, 1, true, true, new Answer("q1", "값")));

		assertThat(saved.get(0).isAiSummaryAgreed()).isFalse();
	}

	@Test
	void AC9_건너뛴_필수_문항은_검사하지_않고_형식이_틀린_답도_거부_없이_버린다() {
		givenForm(FormStatus.OPEN, 1, null, null, definition("section_page", List.of(
				section("s1", question("q1", "multiple_choice", true, YES_NO, rule("all", toSection("s3"), when("q1", "아니오")))),
				section("s2", formatted("q2", true, "date")),
				section("s3", text("q3", true)))));

		service(NOW).submit(command(null, 1, true, null, new Answer("q1", "아니오"), new Answer("q2", "날짜 아님"),
				new Answer("q3", "값")));

		assertThat(saved.get(0).getContent()).containsExactly(new Answer("q1", "아니오"), new Answer("q3", "값"));
	}

	@Test
	void AC15_결과는_제출_버전의_문구와_저장된_답과_제출_시각이다() {
		givenForm(FormStatus.OPEN, 1, null, null, SIMPLE);

		SubmissionResult result = service(NOW).submit(valid());

		assertThat(result.responseId()).isEqualTo(saved.get(0).getId());
		assertThat(result.submittedAt()).isEqualTo(NOW);
		assertThat(saved.get(0).getSubmittedAt()).isEqualTo(NOW);
		assertThat(result.content())
			.containsExactly(new SubmissionResult.SubmittedAnswer("q1", "q1 문구", "값"));
	}

	@Test
	void 운영_설정은_버전_정의가_아니라_forms의_현재_값을_쓴다() {
		Map<String, Object> snapshot = new HashMap<>(SIMPLE);
		snapshot.put("deadline", "2020-01-01T00:00:00Z");
		givenForm(FormStatus.OPEN, 1, null, null, snapshot);

		service(NOW).submit(valid());

		assertThat(saved).hasSize(1);
	}

	@Test
	void 판정_순서는_버전_닫힘_동의_답_순이다() {
		givenForm(FormStatus.CLOSED, 2, null, null, SIMPLE);
		assertRejected(NOW, command(null, 1, false, null), ErrorCode.FORM_VERSION_CHANGED);
		assertRejected(NOW, command(null, 2, false, null), ErrorCode.FORM_CLOSED);

		givenForm(FormStatus.OPEN, 2, null, null, SIMPLE);
		assertRejected(NOW, command(null, 2, false, null), ErrorCode.CONSENT_REQUIRED);
		assertRejected(NOW, command(null, 2, true, null), ErrorCode.REQUIRED_MISSING);
	}

}
