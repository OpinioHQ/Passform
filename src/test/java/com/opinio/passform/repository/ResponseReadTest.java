package com.opinio.passform.repository;

import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ConditionEvaluator;
import com.opinio.passform.domain.analysis.EvaluationResult;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

// AC20: 결과·분석·내보내기는 submitted이면서 제작자가 삭제하지 않은 응답만 다룬다.
@Transactional
class ResponseReadTest extends DatabaseTest {

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		userId = users.saveAndFlush(TestData.newUser()).getId();
		formId = newForm();
	}

	@Test
	void AC20_작성_중_중단_제작자_삭제_다른_폼의_응답은_빠진다() {
		UUID kept = responses.saveAndFlush(TestData.submitted(formId, userId, 1, NOW)).getId();
		responses.saveAndFlush(TestData.withStatus(formId, userId, ResponseStatus.IN_PROGRESS));
		responses.saveAndFlush(TestData.withStatus(formId, null, ResponseStatus.ABANDONED));
		UUID deleted = responses.saveAndFlush(TestData.submitted(formId, userId, 1, NOW)).getId();
		jdbc.update("update responses set creator_deleted_at = ? where response_id = ?",
				java.sql.Timestamp.from(NOW), deleted);
		responses.saveAndFlush(TestData.submitted(newForm(), userId, 1, NOW));

		List<Response> read = responses.findSubmittedNotDeletedByFormId(formId);

		assertThat(read).extracting(Response::getId).containsExactly(kept);
	}

	@Test
	void AC20_모든_버전의_응답을_버전_제출_시각_ID_순으로_읽는다() {
		Instant early = NOW.minusSeconds(60);
		UUID v2 = responses.saveAndFlush(TestData.submitted(formId, null, 2, early)).getId();
		UUID v1Late = responses.saveAndFlush(TestData.submitted(formId, null, 1, NOW)).getId();
		UUID v1Early = responses.saveAndFlush(TestData.submitted(formId, null, 1, early)).getId();
		UUID sameTimeA = responses.saveAndFlush(TestData.submitted(formId, null, 3, NOW)).getId();
		UUID sameTimeB = responses.saveAndFlush(TestData.submitted(formId, null, 3, NOW)).getId();
		List<UUID> sameTime = List.of(sameTimeA, sameTimeB).stream().sorted(ResponseReadTest::uuidOrder).toList();

		List<Response> read = responses.findSubmittedNotDeletedByFormId(formId);

		assertThat(read).extracting(Response::getId)
			.containsExactly(v1Early, v1Late, v2, sameTime.get(0), sameTime.get(1));
		assertThat(read).extracting(Response::getFormVersion).containsExactly(1, 1, 2, 3, 3);
	}

	@Test
	void 응답이_없으면_빈_목록이다() {
		assertThat(responses.findSubmittedNotDeletedByFormId(formId)).isEmpty();
	}

	@Test
	void 읽은_응답을_ResponseRecord로_바꿔_분석에_쓸_수_있다() {
		List<Answer> answers = List.of(new Answer("q1", "소프트웨어학과"), new Answer("q3", List.of("기획", "디자인")),
				new Answer("q4", ""), new Answer("q5", List.of()), new Answer("q6", null));
		UUID responseId = responses
			.saveAndFlush(new Response(formId, null, 2, ResponseStatus.SUBMITTED, answers, false, null, NOW))
			.getId();
		responses.saveAndFlush(TestData.withStatus(formId, null, ResponseStatus.IN_PROGRESS));

		List<ResponseRecord> records = responses.findSubmittedNotDeletedByFormId(formId)
			.stream()
			.map(Response::toResponseRecord)
			.toList();

		assertThat(records).containsExactly(new ResponseRecord(responseId.toString(), 2, ResponseStatus.SUBMITTED,
				Map.of("q1", List.of("소프트웨어학과"), "q3", List.of("기획", "디자인"))));
		EvaluationResult result = new ConditionEvaluator().evaluate(List.of(), null, List.of(), records);
		assertThat(result.responseIds()).containsExactly(responseId.toString());
	}

	private UUID newForm() {
		return forms.saveAndFlush(new Form(userId, Map.of("title", "폼"), NOW)).getId();
	}

	// Postgres의 uuid 정렬은 바이트 순이고 소문자 16진 문자열 순과 같다. UUID.compareTo는 부호 있는 비교라 다르다.
	private static int uuidOrder(UUID left, UUID right) {
		return left.toString().compareTo(right.toString());
	}

}
