package com.opinio.passform.domain.analysis;

import java.util.List;
import java.util.Map;

// SPEC 5절 241행: 응답의 response_id·form_version·answers (단일 값도 1개짜리 목록, 비운 문항은 키 없음)
public record ResponseRecord(String responseId, int formVersion, ResponseStatus status, Map<String, List<String>> answers) {
}
