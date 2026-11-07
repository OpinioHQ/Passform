package com.opinio.passform.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

// 형식 검증(SPEC 5절 174행). 저장할 때마다 form.schema.json으로 전체 정의를 검사한다. 초안도 예외가 없다.
@Component
public class FormSchemaValidator {

	private static final String SCHEMA_PATH = "passform/schemas/form.schema.json";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Schema schema;

	public FormSchemaValidator() {
		try (InputStream input = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
			schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(input);
		}
		catch (IOException e) {
			throw new UncheckedIOException(SCHEMA_PATH + "를 읽지 못했다", e);
		}
	}

	public ValidatedDefinition validate(Object body) {
		List<Error> errors = schema.validate(JSON.valueToTree(body));
		if (!errors.isEmpty()) {
			throw new DomainException(errors.stream().allMatch(FormSchemaValidator::isPaymentLinkPattern)
					? ErrorCode.INVALID_PAYMENT_LINK
					: ErrorCode.SCHEMA_INVALID);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> definition = new LinkedHashMap<>((Map<String, Object>) body);
		Instant deadline = deadlineOf(definition);
		if (deadline != null) {
			definition.put("deadline", deadline.toString());
		}
		Map<String, Object> settings = objectAt(definition, "settings");
		return new ValidatedDefinition(definition, deadline, Boolean.TRUE.equals(settings.get("editable")),
				Boolean.TRUE.equals(settings.get("cancellable")), responseLimitOf(settings), aiSummaryEnabled(definition));
	}

	// 설정이 없으면 꺼짐이다(ARCHITECTURE 10절 F2).
	public static boolean aiSummaryEnabled(Map<String, Object> definition) {
		return Boolean.TRUE.equals(objectAt(objectAt(definition, "settings"), "ai_summary").get("enabled"));
	}

	// https가 아닌 문자열만 invalid_payment_link다(AC11). 타입이 틀리면 schema_invalid다.
	private static boolean isPaymentLinkPattern(Error error) {
		return "pattern".equals(error.getKeyword()) && error.getInstanceLocation().toString().endsWith("payment_link");
	}

	// ISO 8601(타임존 포함)로 받아 UTC로 저장한다(SPEC 5절 181행). 라이브러리의 format 검사 설정에 기대지 않는다.
	private static Instant deadlineOf(Map<String, Object> definition) {
		if (!(definition.get("deadline") instanceof String text)) {
			return null;
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException e) {
			throw new DomainException(ErrorCode.SCHEMA_INVALID);
		}
	}

	private static Integer responseLimitOf(Map<String, Object> settings) {
		if (!(settings.get("response_limit") instanceof Number limit)) {
			return null;
		}
		if (limit.doubleValue() > Integer.MAX_VALUE) {
			throw new DomainException(ErrorCode.SCHEMA_INVALID);
		}
		return limit.intValue();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> objectAt(Map<String, Object> parent, String key) {
		return parent.get(key) instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
	}

	public record ValidatedDefinition(Map<String, Object> definition, Instant deadline, boolean editable,
			boolean cancellable, Integer responseLimit, boolean aiSummaryEnabled) {
	}

}
