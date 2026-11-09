package com.opinio.passform.domain.export;

import java.util.Arrays;
import java.util.Optional;

// POST /forms/{form_id}/export의 format 값과 응답 Content-Type이다(SPEC 239행).
public enum ExportFormat {

	JSON("json", "application/json"), YAML("yaml", "application/yaml"), CSV("csv", "text/csv;charset=UTF-8");

	private final String value;

	private final String contentType;

	ExportFormat(String value, String contentType) {
		this.value = value;
		this.contentType = contentType;
	}

	public String contentType() {
		return contentType;
	}

	public static Optional<ExportFormat> of(String value) {
		return Arrays.stream(values()).filter(format -> format.value.equals(value)).findFirst();
	}

}
