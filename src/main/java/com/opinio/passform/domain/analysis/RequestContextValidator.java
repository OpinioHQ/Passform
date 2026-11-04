package com.opinio.passform.domain.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;

// 파서 출력과 {context} 요청을 강의 3 스키마로 서버에서 다시 검증한다(AGENTS 5절, AC32).
@Component
public class RequestContextValidator {

	private static final String SCHEMA_PATH = "response_analysis/schemas/request_context.schema.json";

	private final Schema schema;

	public RequestContextValidator() {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(SCHEMA_PATH)) {
			if (input == null) {
				throw new IllegalStateException("스키마가 클래스패스에 없습니다: " + SCHEMA_PATH);
			}
			String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(text);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	public List<String> errors(JsonNode node) {
		return schema.validate(node).stream().map(Error::toString).toList();
	}

	public RequestContext toContext(JsonNode node) {
		List<String> conditions = new ArrayList<>();
		node.path("conditions").forEach(condition -> conditions.add(condition.asString()));
		JsonNode sort = node.get("sort");
		return new RequestContext(conditions, text(node, "match"), text(node, "operation"),
				(sort != null) ? new RequestContext.Sort(text(sort, "by"), text(sort, "order")) : null,
				text(node, "target"), text(node, "topic"));
	}

	private static String text(JsonNode node, String key) {
		return node.hasNonNull(key) ? node.get(key).asString() : null;
	}

}
