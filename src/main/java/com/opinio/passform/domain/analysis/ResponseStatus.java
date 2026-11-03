package com.opinio.passform.domain.analysis;

// SPEC 5절 307행: AI 분석 범위는 모든 버전의 submitted 응답 (in_progress·abandoned 제외)
public enum ResponseStatus {
	IN_PROGRESS, SUBMITTED, ABANDONED
}
