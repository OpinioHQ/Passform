package com.opinio.passform.domain.error;

import java.util.Locale;

// API 오류 코드. HTTP 상태는 여기 두지 않고 advice(ApiExceptionHandler.statusOf)에서만 정한다.
// 코드를 더할 때: 이 enum, statusOf의 switch, ApiExceptionHandlerTest의 EXPECTED 표에 한 줄씩 더한다.
public enum ErrorCode {

	NOT_FOUND, FORBIDDEN, INVALID_REQUEST, INTERNAL_ERROR,
	SCHEMA_INVALID, INVALID_PAYMENT_LINK, INVALID_BRANCH, INVALID_DISPLAY_MODE, CONSENT_TEMPLATE_REQUIRED,
	AI_SUMMARY_LOCKED, INVALID_TRANSITION, RESPONSE_LIMIT_REACHED, FORM_CLOSED;

	// 응답의 error 값이다(예: not_found).
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

}
