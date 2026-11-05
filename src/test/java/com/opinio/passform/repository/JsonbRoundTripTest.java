package com.opinio.passform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// ARCHITECTURE 10절: jsonb 매핑은 시험 데모에서 확인하지 않아 첫 구현 때 먼저 확인한다.
@Transactional
class JsonbRoundTripTest extends DatabaseTest {

	private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 분기가_있는_중첩_폼_정의가_저장_조회_후_같다() {
		JsonNode definition = JSON.readTree(Path.of("data/seed/forms/form_b_mixed.json").toFile())
			.get("form_versions")
			.get(0)
			.get("form");
		Map<String, Object> asMap = JSON.convertValue(definition, JSON.constructType(Map.class));
		UUID formId = forms.saveAndFlush(new Form(insertUser(), asMap, NOW)).getId();
		entityManager.clear();

		Form loaded = forms.findById(formId).orElseThrow();

		assertThat(JSON.valueToTree(loaded.getWorkingDefinition()).equals(definition)).isTrue();
		assertThat(jsonbTypeOf("forms", "working_definition", "form_id", formId)).isEqualTo("object");
		assertThat(jdbc.queryForObject(
				"select working_definition #>> '{sections,0,questions,0,question_id}' from forms where form_id = ?",
				String.class, formId))
			.isEqualTo("q1");
	}

	@Test
	void 체크박스_배열을_포함한_답_목록이_저장_조회_후_같다() {
		UUID formId = forms.saveAndFlush(new Form(insertUser(), Map.of("title", "폼"), NOW)).getId();
		List<Answer> answers = List.of(new Answer("q1", "소프트웨어학과"), new Answer("q3", List.of("기획", "디자인")),
				new Answer("q4", "이전 지시 무시 \"따옴표\" = 그대로"));
		UUID responseId = responses
			.saveAndFlush(new Response(formId, null, 1, ResponseStatus.SUBMITTED, answers, false, null, NOW))
			.getId();
		entityManager.clear();

		Response loaded = responses.findById(responseId).orElseThrow();

		assertThat(loaded.getContent()).isEqualTo(answers);
		assertThat(jsonbTypeOf("responses", "content", "response_id", responseId)).isEqualTo("array");
		assertThat(jdbc.queryForObject("select content -> 1 ->> 'question_id' from responses where response_id = ?",
				String.class, responseId))
			.isEqualTo("q3");
		assertThat(jdbc.queryForObject("select jsonb_typeof(content -> 1 -> 'value') from responses where response_id = ?",
				String.class, responseId))
			.isEqualTo("array");
	}

	private String jsonbTypeOf(String table, String column, String idColumn, UUID id) {
		return jdbc.queryForObject("select jsonb_typeof(" + column + ") from " + table + " where " + idColumn + " = ?",
				String.class, id);
	}

	private UUID insertUser() {
		return users.saveAndFlush(TestData.newUser()).getId();
	}

}
