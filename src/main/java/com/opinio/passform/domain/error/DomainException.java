package com.opinio.passform.domain.error;

import java.util.Map;

// 도메인 규칙 위반. details는 SPEC 5절의 추가 필드다(예: question_ids, keys). 키를 응답 그대로 snake_case로 쓴다.
public class DomainException extends RuntimeException {

	private final ErrorCode errorCode;

	private final transient Map<String, Object> details;

	public DomainException(ErrorCode errorCode) {
		this(errorCode, Map.of());
	}

	public DomainException(ErrorCode errorCode, Map<String, Object> details) {
		super(errorCode.code());
		this.errorCode = errorCode;
		this.details = Map.copyOf(details);
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

}
