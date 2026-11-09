package com.opinio.passform.service.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.LiteralMatcher;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.llm.QueryParser;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService.AnalyzeCommand;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 저장한 파서 출력을 실제 AnalysisService에 넣어 코드 보정 뒤의 결과를 얻는다. 파서는 저장값을 돌려주는 모의 객체, 매처는 글자 매칭만 하는
 * 모의 객체, 요약은 모의 객체라 LLM을 부르지 않는다. Gradle 소스 경로 밖에 두며, 실행할 때만 src/test로 복사하고 지운다.
 */
class ParserReplayTest {

	private static final Path INPUT = Path.of("evals/parser_remeasure/replay/input.jsonl");

	private static final Path OUTPUT = Path.of("evals/parser_remeasure/replay/output.jsonl");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonMapper API_JSON = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.build();

	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	private static final UUID FORM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

	@Test
	void 저장한_출력을_분석_흐름에_재생한다() throws IOException {
		List<String> lines = Files.readAllLines(INPUT).stream().filter(line -> !line.isBlank()).toList();
		assertFalse(lines.isEmpty(), "재생 입력이 없다");
		Map<String, Replay> replays = new HashMap<>();
		List<String> results = new ArrayList<>();
		for (String line : lines) {
			JsonNode row = JSON.readTree(line);
			Replay replay = replays.computeIfAbsent(row.get("form").asString(), ParserReplayTest::replayOf);
			results.add(JSON.writeValueAsString(replay.run(row)));
		}
		Files.write(OUTPUT, results);
		assertEquals(lines.size(), results.size());
	}

	private static Replay replayOf(String form) {
		try {
			return new Replay(form);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final class Replay {

		private final AnalysisService service;

		private final String[] output = { null };

		@SuppressWarnings("unchecked")
		private Replay(String formName) throws IOException {
			Map<String, Object> fixture = JSON.readValue(Files.readString(Path.of("data/seed/forms/" + formName + ".json")),
					Map.class);
			FormVersionRepository formVersions = mock(FormVersionRepository.class);
			Map<FormVersionId, FormVersion> versions = new HashMap<>();
			int latest = 0;
			Map<String, Object> latestDefinition = Map.of();
			for (Object item : (List<Object>) fixture.get("form_versions")) {
				Map<String, Object> entry = (Map<String, Object>) item;
				int version = (Integer) entry.get("form_version");
				Map<String, Object> definition = (Map<String, Object>) entry.get("form");
				versions.put(new FormVersionId(FORM_ID, version), new FormVersion(FORM_ID, version, definition, Instant.EPOCH));
				if (version > latest) {
					latest = version;
					latestDefinition = definition;
				}
			}
			when(formVersions.findById(any(FormVersionId.class)))
				.thenAnswer(call -> Optional.ofNullable(versions.get(call.<FormVersionId>getArgument(0))));

			Form form = new Form(OWNER, latestDefinition, Instant.EPOCH);
			ReflectionTestUtils.setField(form, "id", FORM_ID);
			ReflectionTestUtils.setField(form, "definitionVersion", latest);
			FormRepository forms = mock(FormRepository.class);
			when(forms.findById(FORM_ID)).thenReturn(Optional.of(form));

			ResponseRepository responses = mock(ResponseRepository.class);
			when(responses.findSubmittedNotDeletedByFormId(FORM_ID)).thenReturn(submitted(fixture));

			QueryParser parser = mock(QueryParser.class);
			when(parser.parse(anyString(), nullable(String.class))).thenAnswer(call -> output[0]);

			ConditionMatcher literalOnly = (conditions, formVersionList) -> {
				List<ConditionMatch> matches = new ArrayList<>();
				conditions.forEach(condition -> formVersionList
					.forEach(version -> matches.addAll(LiteralMatcher.match(condition, version))));
				return matches;
			};
			SummaryService summaries = mock(SummaryService.class);
			when(summaries.summarize(any(), anyList(), anyList()))
				.thenReturn(new SummaryService.Outcome(new SummaryView("ok", 0, List.of(), null), List.of()));
			service = new AnalysisService(forms, formVersions, responses, parser, literalOnly, new RequestContextSchema(),
					summaries);
		}

		private ObjectNode run(JsonNode row) {
			output[0] = JSON.writeValueAsString(row.get("output"));
			String body = JSON.writeValueAsString(Map.of("query", row.get("query").asString()));
			ObjectNode result = ((ObjectNode) row).deepCopy();
			result.remove("output");
			result.remove("query");
			try {
				JsonNode view = API_JSON.valueToTree(service.analyze(new AnalyzeCommand(OWNER, FORM_ID.toString(), body)));
				result.put("status", view.path("status").asString());
				result.set("corrected", view.path("context"));
				result.set("reason", view.path("clarify").path("reason"));
				result.set("count", view.path("result").path("count"));
				result.set("not_evaluable", view.path("coverage").path("not_evaluable_count"));
			}
			catch (DomainException e) {
				result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			return result;
		}

		@SuppressWarnings("unchecked")
		private static List<Response> submitted(Map<String, Object> fixture) {
			List<Map<String, Object>> rows = new ArrayList<>();
			((List<Object>) fixture.get("responses")).forEach(row -> rows.add((Map<String, Object>) row));
			List<String> seedIds = rows.stream().map(row -> (String) row.get("response_id")).sorted().toList();
			List<Response> submitted = new ArrayList<>();
			for (Map<String, Object> row : rows) {
				if (!"submitted".equals(row.get("status"))) {
					continue;
				}
				List<Answer> answers = new ArrayList<>();
				for (Object answer : (List<Object>) row.get("answers")) {
					Map<String, Object> entry = (Map<String, Object>) answer;
					answers.add(new Answer((String) entry.get("question_id"), entry.get("value")));
				}
				Response response = new Response(FORM_ID, OWNER, (Integer) row.get("form_version"), ResponseStatus.SUBMITTED,
						answers, Boolean.TRUE.equals(row.get("ai_summary_agreed")), null,
						Instant.parse((String) row.get("submitted_at")));
				int index = seedIds.indexOf(row.get("response_id")) + 1;
				ReflectionTestUtils.setField(response, "id", UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index)));
				submitted.add(response);
			}
			submitted.sort(Comparator.comparing(Response::getFormVersion)
				.thenComparing(Response::getSubmittedAt)
				.thenComparing(Response::getId));
			return submitted;
		}

	}

}
