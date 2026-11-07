package com.opinio.passform.domain.form;

import java.util.Locale;

// 초안·배포·마감. 전이 규칙은 SPEC 5절 175행이며 Form.checkTransition에 있다.
public enum FormStatus {
	DRAFT, OPEN, CLOSED;

	// API의 status 값이다(예: draft). DB에는 enum 이름(대문자)으로 저장한다.
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}
}
