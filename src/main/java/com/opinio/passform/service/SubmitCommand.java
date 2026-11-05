package com.opinio.passform.service;

import java.util.List;
import java.util.UUID;

import com.opinio.passform.domain.response.Answer;

// 제출 요청. userId는 비로그인이면 null이다. consentAgreed, aiSummaryAgreed는 생략하면 null이다.
public record SubmitCommand(UUID formId, UUID userId, int formVersion, List<Answer> answers, Boolean consentAgreed,
		Boolean aiSummaryAgreed) {
}
