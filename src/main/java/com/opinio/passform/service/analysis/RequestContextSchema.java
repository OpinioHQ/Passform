package com.opinio.passform.service.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;

// 파서 출력과 {context}를 request_context.schema.json으로 서버에서 다시 검증한다(AC30, AC32).
@Component
public class RequestContextSchema {

	private static final String SCHEMA_PATH = "response_analysis/schemas/request_context.schema.json";

	private final Schema schema;

	public RequestContextSchema() {
		try (InputStream input = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
			schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(input);
		}
		catch (IOException e) {
			throw new UncheckedIOException(SCHEMA_PATH + "를 읽지 못했다", e);
		}
	}

	// 맞으면 빈 목록이다. 파서 재호출 때 이 오류 설명을 붙인다.
	List<String> errors(JsonNode value) {
		return schema.validate(value).stream().map(error -> error.getMessage()).toList();
	}

	// 스키마를 통과한 값만 넘긴다.
	static RequestContext toContext(JsonNode value) {
		List<String> conditions = new ArrayList<>();
		value.path("conditions").forEach(condition -> conditions.add(condition.asString()));
		JsonNode sort = value.path("sort");
		return new RequestContext(List.copyOf(conditions), textOrNull(value.path("match")),
				textOrNull(value.path("operation")),
				sort.isObject() ? new RequestContext.Sort(sort.path("by").asString(), sort.path("order").asString()) : null,
				textOrNull(value.path("target")), textOrNull(value.path("topic")));
	}

	private static String textOrNull(JsonNode value) {
		return value.isString() ? value.asString() : null;
	}

}
