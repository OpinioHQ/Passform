package com.opinio.passform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.ConflictException;
import com.opinio.passform.domain.ForbiddenException;
import com.opinio.passform.domain.NotFoundException;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.ConsentTemplate;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormDefinition;
import com.opinio.passform.domain.form.FormJson;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.response.SavedForm;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.repository.ConsentTemplateRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.UserRepository;

class QueryServicesTest {

	private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");

	private static final String BRANCHING = """
			{"title":"폼","note":"설명","sections":[{"section_id":"s","title":"s","questions":[
			 {"question_id":"q1","question_note":"이름","question_type":"short_answer","input_format":{"kind":"name"}},
			 {"question_id":"q2","question_note":"소개","question_type":"long_answer"},
			 {"question_id":"q3","question_note":"학년","question_type":"multiple_choice","options":["1학년"],
			  "branch_rules":[{"when":{"match":"all","conditions":[{"question_id":"q3","option":"1학년"}]},"go_to":"submit"}]}]}]}""";

	private final FormRepository forms = mock(FormRepository.class);

	private final FormVersionRepository versions = mock(FormVersionRepository.class);

	private final ConsentTemplateRepository consents = mock(ConsentTemplateRepository.class);

	private final ResponseRepository responses = mock(ResponseRepository.class);

	private final SavedFormRepository savedForms = mock(SavedFormRepository.class);

	private final UserRepository users = mock(UserRepository.class);

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private final FormQueryService query = new FormQueryService(forms, versions, consents, clock);

	private final MeService me = new MeService(users, forms, versions, responses, savedForms, clock, "http://localhost");

	private static Form form(String id, FormStatus status, Instant deadline, boolean ai) {
		return new Form(id, "owner", id + "-slug", status, "제목 " + id, 1, deadline, null, true, false, ai, "c", "section_page", NOW);
	}

	private static Response submitted(String id, String formId, Instant at) {
		return new Response(id, formId, "user", 1, ResponseStatus.SUBMITTED, "[{\"question_id\":\"q2\",\"value\":\"안녕\"}]", false, at, null);
	}

	// ---- 공개 조회

	@Test
	void 공개_조회는_동의_항목을_입력_형식으로_만들고_AI_고지를_붙인다() {
		Form form = form("f", FormStatus.OPEN, null, true);
		when(forms.findBySlug("f-slug")).thenReturn(Optional.of(form));
		when(versions.findByFormIdAndVersion("f", 1)).thenReturn(Optional.of(new FormVersion("f", 1, BRANCHING)));
		when(consents.findById("c")).thenReturn(Optional.of(new ConsentTemplate("c", "목적", "보유")));
		FormQueryService.PublicFormView view = query.bySlug("f-slug");
		assertThat(view.consent().items()).containsExactly("이름", "문항별 응답 내용");
		assertThat(view.consent().aiSummary().provider()).isEqualTo("OpenAI");
		assertThat(view.display().allowedModes()).containsExactly("section_page");
		assertThat(view.sections().size()).isEqualTo(1);
	}

	@Test
	void 분기가_없고_요약이_꺼진_폼은_두_보기_방식과_고지_없음이다() {
		Form form = form("f", FormStatus.OPEN, null, false);
		when(forms.findById("f")).thenReturn(Optional.of(form));
		when(versions.findByFormIdAndVersion("f", 1)).thenReturn(Optional.of(new FormVersion("f", 1, """
				{"title":"폼","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"q1","question_note":"소개","question_type":"long_answer"}]}]}""")));
		when(consents.findById("c")).thenReturn(Optional.of(new ConsentTemplate("c", "목적", "보유")));
		FormQueryService.PublicFormView view = query.byId("f");
		assertThat(view.display().allowedModes()).containsExactly("one_question", "all_questions");
		assertThat(view.consent().aiSummary()).isNull();
	}

	@Test
	void AC41_초안은_404_마감은_409다() {
		when(forms.findById("draft")).thenReturn(Optional.of(form("draft", FormStatus.DRAFT, null, false)));
		assertThatThrownBy(() -> query.byId("draft")).isInstanceOf(NotFoundException.class);
		when(forms.findById("late")).thenReturn(Optional.of(form("late", FormStatus.OPEN, NOW.minusSeconds(1), false)));
		assertThatThrownBy(() -> query.byId("late")).isInstanceOf(ConflictException.class);
	}

	// ---- 내 폼함·이력

	@Test
	void 내_폼함은_미제출_마감순_다음_제출_최근순이다() {
		when(savedForms.findByUserId("user")).thenReturn(List.of(new SavedForm("user", "c", SavedForm.SUBMITTED),
				new SavedForm("user", "d", SavedForm.SUBMITTED), new SavedForm("user", "a", SavedForm.SAVED),
				new SavedForm("user", "b", SavedForm.SAVED), new SavedForm("user", "e", SavedForm.SAVED)));
		when(forms.findById("a")).thenReturn(Optional.of(form("a", FormStatus.OPEN, NOW.plusSeconds(200), false)));
		when(forms.findById("b")).thenReturn(Optional.of(form("b", FormStatus.OPEN, NOW.plusSeconds(100), false)));
		when(forms.findById("e")).thenReturn(Optional.of(form("e", FormStatus.OPEN, null, false)));
		when(forms.findById("c")).thenReturn(Optional.of(form("c", FormStatus.OPEN, null, false)));
		when(forms.findById("d")).thenReturn(Optional.of(form("d", FormStatus.OPEN, null, false)));
		when(responses.findByUserIdAndStatusOrderBySubmittedAtDescIdAsc("user", ResponseStatus.SUBMITTED))
			.thenReturn(List.of(submitted("r2", "d", NOW), submitted("r1", "c", NOW.minusSeconds(100))));
		assertThat(me.myForms("user")).extracting(MeService.SavedFormView::formId).containsExactly("b", "a", "e", "d", "c");
	}

	@Test
	void AC15_이력은_제출_버전의_문구로_보여_주고_본인만_본다() {
		Response response = submitted("r1", "f", NOW);
		when(responses.findById("r1")).thenReturn(Optional.of(response));
		when(forms.findById("f")).thenReturn(Optional.of(form("f", FormStatus.OPEN, null, false)));
		when(versions.findByFormIdAndVersion("f", 1)).thenReturn(Optional.of(new FormVersion("f", 1, BRANCHING)));
		MeService.ResponseDetail detail = me.historyDetail("user", "r1");
		assertThat(detail.formTitle()).isEqualTo("폼");
		assertThat(detail.content()).singleElement().satisfies(answer -> {
			assertThat(answer.questionNote()).isEqualTo("소개");
			assertThat(answer.value()).isEqualTo("안녕");
		});
		assertThat(detail.editable()).isTrue();
		assertThat(detail.cancellable()).isFalse();
		assertThatThrownBy(() -> me.historyDetail("다른사람", "r1")).isInstanceOf(NotFoundException.class);
	}

	@Test
	void 제출_이력과_내가_만든_폼_목록을_돌려준다() {
		Form form = form("f", FormStatus.OPEN, null, false);
		when(forms.findById("f")).thenReturn(Optional.of(form));
		when(versions.findByFormIdAndVersion("f", 1)).thenReturn(Optional.of(new FormVersion("f", 1, BRANCHING)));
		when(responses.findByUserIdAndStatusOrderBySubmittedAtDescIdAsc("user", ResponseStatus.SUBMITTED))
			.thenReturn(List.of(submitted("r1", "f", NOW)));
		assertThat(me.history("user")).singleElement().satisfies(item -> assertThat(item.formTitle()).isEqualTo("폼"));

		when(forms.findByOwnerIdOrderByCreatedAtDescIdAsc("owner")).thenReturn(List.of(form));
		when(responses.countByFormIdAndStatus("f", ResponseStatus.SUBMITTED)).thenReturn(7L);
		assertThat(me.createdForms("owner")).singleElement().satisfies(item -> {
			assertThat(item.status()).isEqualTo("open");
			assertThat(item.shareUrl()).isEqualTo("http://localhost/f/f-slug");
			assertThat(item.responseCount()).isEqualTo(7);
		});
	}

	@Test
	void 내_폼함_저장은_한_번만_넣고_남의_초안은_404다() {
		when(forms.findById("f")).thenReturn(Optional.of(form("f", FormStatus.OPEN, null, false)));
		when(savedForms.existsById(SavedForm.idOf("user", "f"))).thenReturn(false, true);
		assertThat(me.saveForm("user", "f")).isTrue();
		assertThat(me.saveForm("user", "f")).isFalse();
		when(forms.findById("draft")).thenReturn(Optional.of(form("draft", FormStatus.DRAFT, null, false)));
		assertThatThrownBy(() -> me.saveForm("user", "draft")).isInstanceOf(NotFoundException.class);
	}

	@Test
	void 화면_모드는_정해진_값만_받는다() {
		User user = new User("user", "이름", "a@b.c");
		when(users.findById("user")).thenReturn(Optional.of(user));
		assertThat(me.changeUiMode("user", "creator").uiMode()).isEqualTo("creator");
		assertThat(me.changeUiMode("user", "admin").uiMode()).isEqualTo("creator");
		assertThat(me.me("user").deadlineReminder()).isTrue();
	}

	// ---- 결과

	@Test
	void AC19_AC62_결과는_통합과_버전별로_세고_다른_폼의_응답은_404다() {
		FormDefinition definition = FormJson.parseDefinition("""
				{"title":"폼","sections":[{"section_id":"s","title":"s","questions":[
				 {"question_id":"q1","question_note":"찬성","question_type":"multiple_choice","options":["예","아니요"]}]}]}""");
		Response first = new Response("r1", "f", null, 1, ResponseStatus.SUBMITTED, "[{\"question_id\":\"q1\",\"value\":\"예\"}]", false, NOW, null);
		ResponseRecord record = new ResponseRecord("r1", 1, ResponseStatus.SUBMITTED, Map.of("q1", List.of("예")));
		FormSnapshotLoader loader = mock(FormSnapshotLoader.class);
		when(loader.loadOwned(any(), any())).thenReturn(new FormSnapshot(form("f", FormStatus.OPEN, null, false),
				Map.of(1, definition), List.of(first), List.of(record)));
		ResultService service = new ResultService(loader);
		ResultService.ResultsView results = service.results("f", "owner");
		assertThat(results.responseCount()).isEqualTo(1);
		assertThat(results.versions()).singleElement().satisfies(version -> assertThat(version.questions().get(0).counts()).containsEntry("예", 1).containsEntry("아니요", 0));
		assertThat(service.response("f", "owner", "r1").content()).singleElement()
			.satisfies(answer -> assertThat(answer.questionNote()).isEqualTo("찬성"));
		assertThatThrownBy(() -> service.response("f", "owner", "남의응답")).isInstanceOf(NotFoundException.class);
	}

	@Test
	void AC2_소유자가_아니면_스냅샷을_읽지_못한다() {
		FormRepository repository = mock(FormRepository.class);
		when(repository.findById("f")).thenReturn(Optional.of(form("f", FormStatus.OPEN, null, false)));
		FormSnapshotLoader loader = new FormSnapshotLoader(repository, versions, responses);
		assertThatThrownBy(() -> loader.loadOwned("f", "다른사람")).isInstanceOf(ForbiddenException.class);
		when(repository.findById("없음")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> loader.loadOwned("없음", "owner")).isInstanceOf(NotFoundException.class);

		when(versions.findByFormIdOrderByVersionAsc("f")).thenReturn(List.of(new FormVersion("f", 1, BRANCHING)));
		when(responses.findByFormIdAndStatus("f", ResponseStatus.SUBMITTED)).thenReturn(List.of(submitted("r1", "f", NOW)));
		FormSnapshot snapshot = loader.loadOwned("f", "owner");
		assertThat(snapshot.records()).singleElement().satisfies(record -> assertThat(record.answers()).containsKey("q2"));
		assertThat(snapshot.latestVersion()).isEqualTo(1);
		assertThat(snapshot.formTexts()).contains("소개", "1학년");
		assertThat(snapshot.question(1, "q2")).isPresent();
	}

}
