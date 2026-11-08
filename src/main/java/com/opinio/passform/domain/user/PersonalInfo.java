package com.opinio.passform.domain.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// personal_info의 닫힌 키 목록(SPEC 5절 107행, AC3). 오류에는 키 이름만 넣고 값은 넣지 않는다.
// 사람이 정한 것(2026-11-08): 값은 문자열만 받고, 앞뒤 공백을 지워 비면 그 키를 저장하지 않는다. 형식은 제출할 때 검사한다.
// NUL 문자는 Postgres가 거부해 500이 되고 오류 문맥에 값이 섞일 수 있어 저장 전에 거절한다.
public final class PersonalInfo {

	private static final Set<String> ALLOWED_KEYS = Set.of("name", "student_id", "department", "phone_number", "email",
			"address", "birth_date");

	private PersonalInfo() {
	}

	// 목록 밖 키가 하나라도 있으면 값을 보기 전에 거절한다.
	static Map<String, String> validated(Map<String, Object> raw) {
		if (raw == null) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		Set<String> unsupported = new TreeSet<>(raw.keySet());
		unsupported.removeAll(ALLOWED_KEYS);
		if (!unsupported.isEmpty()) {
			throw new DomainException(ErrorCode.UNSUPPORTED_KEY, Map.of("keys", List.copyOf(unsupported)));
		}
		Map<String, String> values = new LinkedHashMap<>();
		raw.forEach((key, value) -> {
			if (!(value instanceof String text) || text.indexOf('\0') >= 0) {
				throw new DomainException(ErrorCode.INVALID_REQUEST);
			}
			if (!text.isBlank()) {
				values.put(key, text.strip());
			}
		});
		return values;
	}

}
