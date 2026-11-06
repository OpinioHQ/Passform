package com.opinio.passform.domain.form;

import java.util.List;
import java.util.Map;

// 폼 정의 JSON(Map)에서 값을 읽는 도우미. 값이 없거나 형태가 다르면 빈 값으로 읽는다. 형식은 form.schema.json이 검사한다.
final class FormJson {

	private FormJson() {
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object value) {
		return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
	}

	static List<?> array(Object value) {
		return value instanceof List<?> list ? list : List.of();
	}

	static String text(Object value) {
		return value instanceof String text ? text : null;
	}

}
