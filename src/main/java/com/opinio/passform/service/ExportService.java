package com.opinio.passform.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.export.ExportFormat;
import com.opinio.passform.domain.export.ExportWriter;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.service.analysis.AnalysisService;
import com.opinio.passform.service.analysis.AnalysisService.ExportSelection;
import com.opinio.passform.service.analysis.AnalyzeView.ClarifyView;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// POST /forms/{form_id}/export (SPEC 239~244행). context 필터가 LLM 매처를 부르므로 트랜잭션을 걸지 않는다.
// 사람이 정한 것(2026-11-09): 잘못된 format·빈 form_versions·없는 버전은 400 invalid_request, 응답이 있는 버전만 넣고 0건은 빈 파일이다.
@Service
public class ExportService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Set<String> KEYS = Set.of("format", "form_versions", "context");

	private final AnalysisService analysis;

	private final FormVersionRepository formVersions;

	private final ResponseRepository responses;

	private final FormSchemaValidator schemaValidator;

	public ExportService(AnalysisService analysis, FormVersionRepository formVersions, ResponseRepository responses,
			FormSchemaValidator schemaValidator) {
		this.analysis = analysis;
		this.formVersions = formVersions;
		this.responses = responses;
		this.schemaValidator = schemaValidator;
	}

	public ExportOutcome export(ExportCommand command) {
		// 본문을 읽기 전에 소유를 확인한다. 형식 오류 때문에 403과 400이 뒤바뀌지 않는다(AC2).
		Form form = analysis.requireOwnedForm(command.userId(), command.formId());
		JsonNode request = readRequest(command.body());
		ExportFormat format = formatOf(request);
		if (request.hasNonNull("context") && request.hasNonNull("form_versions")) {
			throw new DomainException(ErrorCode.INVALID_CONTEXT);
		}

		List<Response> selected;
		if (request.hasNonNull("context")) {
			ExportSelection selection = analysis.selectForExport(form, request.get("context"));
			if (selection.clarify() != null) {
				return new ExportOutcome(null, null, selection.clarify());
			}
			selected = selection.responses();
		}
		else {
			selected = inRange(form.getId(), request);
		}
		Map<Integer, Map<String, Object>> definitions = definitionsOf(form.getId(), selected, format);
		return new ExportOutcome(ExportWriter.write(format, definitions, selected), format.contentType(), null);
	}

	private static JsonNode readRequest(String body) {
		JsonNode request;
		try {
			request = body == null || body.isBlank() ? null : JSON.readTree(body);
		}
		catch (JacksonException e) {
			throw invalidRequest();
		}
		if (request == null || !request.isObject()) {
			throw invalidRequest();
		}
		for (String key : request.propertyNames()) {
			if (!KEYS.contains(key)) {
				throw invalidRequest();
			}
		}
		return request;
	}

	private static ExportFormat formatOf(JsonNode request) {
		Optional<ExportFormat> format = request.path("format").isString()
				? ExportFormat.of(request.get("format").asString()) : Optional.empty();
		return format.orElseThrow(ExportService::invalidRequest);
	}

	// 범위가 없으면 전체 버전이다. 응답을 읽는 경로는 findSubmittedNotDeletedByFormId 하나다(AC20).
	private List<Response> inRange(UUID formId, JsonNode request) {
		List<Response> submitted = responses.findSubmittedNotDeletedByFormId(formId);
		if (!request.hasNonNull("form_versions")) {
			return submitted;
		}
		Set<Integer> range = versionRange(formId, request.get("form_versions"));
		return submitted.stream().filter(response -> range.contains(response.getFormVersion())).toList();
	}

	private Set<Integer> versionRange(UUID formId, JsonNode node) {
		if (!node.isArray() || node.isEmpty()) {
			throw invalidRequest();
		}
		Set<Integer> range = new TreeSet<>();
		for (JsonNode version : node) {
			if (!version.isIntegralNumber() || !version.canConvertToInt()) {
				throw invalidRequest();
			}
			if (!formVersions.existsById(new FormVersionId(formId, version.intValue()))) {
				throw invalidRequest();
			}
			range.add(version.intValue());
		}
		return range;
	}

	// 각 응답의 제출 버전 정의다. 최신 정의를 쓰지 않는다. 파일에 정의를 넣는 형식은 스키마로 다시 검사한다(AGENTS 5절).
	private Map<Integer, Map<String, Object>> definitionsOf(UUID formId, List<Response> selected, ExportFormat format) {
		Map<Integer, Map<String, Object>> definitions = new TreeMap<>();
		for (Response response : selected) {
			definitions.computeIfAbsent(response.getFormVersion(), version -> definitionOf(formId, version, format));
		}
		return definitions;
	}

	private Map<String, Object> definitionOf(UUID formId, int version, ExportFormat format) {
		FormVersion found = formVersions.findById(new FormVersionId(formId, version))
			.orElseThrow(() -> new IllegalStateException("제출 응답의 폼 버전이 없다: " + version));
		if (format != ExportFormat.CSV) {
			requireValid(found.getDefinition(), version);
		}
		return found.getDefinition();
	}

	// 저장된 정의가 틀린 것은 요청자의 잘못이 아니므로 400이 아니라 서버 오류다.
	private void requireValid(Map<String, Object> definition, int version) {
		try {
			schemaValidator.validate(definition);
		}
		catch (DomainException e) {
			throw new IllegalStateException("저장된 폼 정의가 스키마에 맞지 않다: 폼 버전 " + version, e);
		}
	}

	private static DomainException invalidRequest() {
		return new DomainException(ErrorCode.INVALID_REQUEST);
	}

	public record ExportCommand(UUID userId, String formId, String body) {
	}

	// clarify가 있으면 content와 contentType은 null이다.
	public record ExportOutcome(byte[] content, String contentType, ClarifyView clarify) {
	}

}
