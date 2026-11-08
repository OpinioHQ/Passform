package com.opinio.passform.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.PendingConditionMatcher;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;
import com.opinio.passform.service.analysis.AnalyzeView.QuestionOption;

// list 정렬: AC28의 테스트 문장과 AC61의 빈 값·동률 순서. 조건 없는 {context}라 매처를 부르지 않는다.
class ListSortTest {

	private static final UUID OWNER = UUID.randomUUID();

	private static final UUID FORM_ID = UUID.randomUUID();

	// 버전마다 문항 목록과 응답(문항 → 값) 목록. 응답 ID는 나열한 순서대로 커진다.
	private record Version(List<Map<String, Object>> questions, List<Map<String, String>> answers) {
	}

	private static Map<String, Object> question(String id, String note, String type, String... options) {
		return Map.of("question_id", id, "question_note", note, "question_type", type, "options", List.of(options));
	}

	private static Map<String, Object> dateQuestion(String id, String note) {
		return Map.of("question_id", id, "question_note", note, "question_type", "short_answer", "options", List.of(),
				"input_format", Map.of("kind", "date"));
	}

	private static String id(int index) {
		return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index)).toString();
	}

	private static AnalyzeView list(String by, String order, Version... versions) {
		FormVersionRepository formVersions = mock(FormVersionRepository.class);
		Map<FormVersionId, FormVersion> definitions = new HashMap<>();
		List<Response> submitted = new ArrayList<>();
		int next = 1;
		for (int index = 0; index < versions.length; index++) {
			int version = index + 1;
			Map<String, Object> definition = Map.of("sections",
					List.of(Map.of("section_id", "s1", "questions", versions[index].questions())));
			definitions.put(new FormVersionId(FORM_ID, version), new FormVersion(FORM_ID, version, definition, Instant.EPOCH));
			for (Map<String, String> values : versions[index].answers()) {
				List<Answer> answers = new ArrayList<>();
				values.forEach((questionId, value) -> answers.add(new Answer(questionId, value)));
				Response response = new Response(FORM_ID, OWNER, version, ResponseStatus.SUBMITTED, answers, false, null,
						Instant.EPOCH);
				ReflectionTestUtils.setField(response, "id", UUID.fromString(id(next++)));
				submitted.add(response);
			}
		}
		when(formVersions.findById(any(FormVersionId.class)))
			.thenAnswer(call -> Optional.ofNullable(definitions.get(call.<FormVersionId>getArgument(0))));
		Form form = new Form(OWNER, Map.of(), Instant.EPOCH);
		ReflectionTestUtils.setField(form, "id", FORM_ID);
		ReflectionTestUtils.setField(form, "definitionVersion", versions.length);
		FormRepository forms = mock(FormRepository.class);
		when(forms.findById(FORM_ID)).thenReturn(Optional.of(form));
		ResponseRepository responses = mock(ResponseRepository.class);
		when(responses.findSubmittedNotDeletedByFormId(FORM_ID)).thenReturn(submitted);
		AnalysisService service = new AnalysisService(forms, formVersions, responses, mock(QueryParser.class),
				new PendingConditionMatcher(), new RequestContextSchema(), mock(SummaryService.class));
		String sort = "{\"by\":\"" + by + "\",\"order\":\"" + order + "\"}";
		return service.analyze(new AnalyzeCommand(OWNER, FORM_ID.toString(),
				"{\"context\":{\"conditions\":[],\"operation\":\"list\",\"sort\":" + sort + "}}"));
	}

	@Test
	void AC28_장문_문항이면_unsortable_field이고_후보에_없다() {
		AnalyzeView view = list("자기소개", "asc", new Version(
				List.of(question("q1", "학년", "multiple_choice", "1학년", "2학년"), question("q4", "자기소개", "long_answer")),
				List.of(Map.of("q1", "1학년", "q4", "안녕하세요"))));

		assertThat(view.clarify().reason()).isEqualTo("unsortable_field");
		assertThat(view.clarify().options()).isEqualTo(List.of(new QuestionOption(1, "q1", "학년", List.of("1학년", "2학년"))));
	}

	@Test
	void AC28_나이에_23살이_있으면_unsortable_field다() {
		AnalyzeView view = list("나이", "asc", new Version(List.of(question("q2", "나이", "short_answer")),
				List.of(Map.of("q2", "22"), Map.of("q2", "23살"))));

		assertThat(view.clarify().reason()).isEqualTo("unsortable_field");
	}

	@Test
	void AC28_날짜_형식_단답은_날짜순이다() {
		AnalyzeView view = list("생일", "asc", new Version(List.of(dateQuestion("q3", "생일")),
				List.of(Map.of("q3", "2003-05-01"), Map.of("q3", "2001-12-31"), Map.of("q3", "2002-01-15"))));

		assertThat(view.result().responseIds()).containsExactly(id(2), id(3), id(1));
	}

	@Test
	void AC28_버전마다_선택지_순서가_다르면_unsortable_field다() {
		AnalyzeView view = list("학년", "asc",
				new Version(List.of(question("q1", "학년", "multiple_choice", "1학년", "2학년")), List.of(Map.of("q1", "1학년"))),
				new Version(List.of(question("q1", "학년", "multiple_choice", "2학년", "1학년")), List.of(Map.of("q1", "2학년"))));

		assertThat(view.clarify().reason()).isEqualTo("unsortable_field");
	}

	@Test
	void AC28_버전_사이에_선택지_문구가_바뀌면_unsortable_field다() {
		AnalyzeView view = list("학년", "asc",
				new Version(List.of(question("q1", "학년", "multiple_choice", "1학년", "2학년")), List.of(Map.of("q1", "1학년"))),
				new Version(List.of(question("q1", "학년", "multiple_choice", "신입생", "2학년")), List.of(Map.of("q1", "2학년"))));

		assertThat(view.clarify().reason()).isEqualTo("unsortable_field");
	}

	@Test
	void AC28_버전_2에_나이가_없으면_버전_2_응답을_맨_뒤에_두고_알린다() {
		AnalyzeView view = list("나이", "asc",
				new Version(List.of(question("q2", "나이", "short_answer")), List.of(Map.of("q2", "25"), Map.of("q2", "21"))),
				new Version(List.of(question("q1", "학년", "multiple_choice", "1학년")), List.of(Map.of("q1", "1학년"))),
				new Version(List.of(question("q2", "나이", "short_answer")), List.of(Map.of("q2", "23"))));

		assertThat(view.result().responseIds()).containsExactly(id(2), id(4), id(1), id(3));
		assertThat(view.coverage().sortUnavailableByVersion()).containsExactly(2);
	}

	@Test
	void AC61_빈_값은_맨_뒤이고_같은_값은_response_id_오름차순이다() {
		AnalyzeView view = list("학년", "desc", new Version(List.of(question("q1", "학년", "multiple_choice", "1학년", "2학년")),
				List.of(Map.of(), Map.of("q1", "1학년"), Map.of("q1", "2학년"), Map.of("q1", "1학년"), Map.of("q1", "2학년"))));

		assertThat(view.result().responseIds()).containsExactly(id(3), id(5), id(2), id(4), id(1));
		assertThat(view.coverage().sortUnavailableByVersion()).isNull();
	}

}
