package com.opinio.passform.demo.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.opinio.passform.demo.config.AnalysisProperties;
import com.opinio.passform.demo.domain.analysis.RequestContext;
import com.opinio.passform.demo.domain.analysis.RequestContextValidator;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 자연어 → RequestContext. 재호출은 형식 오류에만 1회 한다(AC30, AGENTS 3절 4항).
@Component
public class QueryParser {

	private static final Logger log = LoggerFactory.getLogger(QueryParser.class);

	private static final String PROMPT_PATH = "response_analysis/prompts/parse_query.md";

	private static final String SCHEMA_PATH = "/response_analysis/schemas/request_context.schema.json";

	private final LlmClient client;

	private final RequestContextValidator validator;

	private final String model;

	private final String prompt;

	private final Map<String, Object> schema;

	private final JsonMapper mapper = JsonMapper.builder().build();

	public QueryParser(LlmClient client, RequestContextValidator validator, AnalysisProperties properties) {
		this.client = client;
		this.validator = validator;
		this.model = properties.parserModel();
		this.prompt = PromptLoader.load(PROMPT_PATH);
		this.schema = loadSchema();
	}

	public Optional<RequestContext> parse(String query) {
		// 프롬프트 예시처럼 발화를 그대로 보낸다. 태그로 감싸면 조건을 더 자주 버렸다(docs/demo/NOTES.md).
		String input = query;
		for (int attempt = 1; attempt <= 2; attempt++) {
			List<String> errors = new ArrayList<>();
			List<String> outputs = client.complete(new LlmClient.Request("parse_query", model, prompt, input,
					"request_context", schema, false, true));
			for (String output : outputs) {
				try {
					JsonNode node = mapper.readTree(output);
					List<String> schemaErrors = validator.errors(node);
					if (schemaErrors.isEmpty()) {
						return Optional.of(validator.toContext(node));
					}
					errors.addAll(schemaErrors);
				}
				catch (JacksonException exception) {
					errors.add("JSON이 아닙니다.");
				}
			}
			if (outputs.isEmpty()) {
				errors.add("출력이 비어 있습니다.");
			}
			log.warn("parse_query_invalid attempt={} errors={}", attempt, errors.size());
			input = query + "\n\n이전 출력이 스키마에 맞지 않았다. 오류: " + String.join("; ", errors)
					+ "\n스키마에 맞는 JSON 객체 하나만 다시 출력한다.";
		}
		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> loadSchema() {
		try (var input = getClass().getResourceAsStream(SCHEMA_PATH)) {
			if (input == null) {
				throw new IllegalStateException("스키마가 없습니다: " + SCHEMA_PATH);
			}
			Map<String, Object> loaded = new java.util.HashMap<>(mapper.readValue(input, Map.class));
			// OpenAI 구조화 출력은 $schema 키를 쓰지 않는다. 서버 쪽 재검증은 원본 스키마로 한다.
			loaded.remove("$schema");
			return loaded;
		}
		catch (java.io.IOException exception) {
			throw new java.io.UncheckedIOException(exception);
		}
	}

}
