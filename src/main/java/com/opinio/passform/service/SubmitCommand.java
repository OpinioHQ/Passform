package com.opinio.passform.service;

import java.util.List;
import java.util.UUID;

import com.opinio.passform.domain.response.Answer;

// 제출 요청. userId는 비로그인이면 null이다. consentAgreed, aiSummaryAgreed는 생략하면 null이다.
// draftResponseId는 로그인한 본인의 작성 중 응답을 제출할 때만 쓴다(SPEC 5절 188행, 216행). 없으면 새 응답이다.
public record SubmitCommand(UUID formId, UUID userId, int formVersion, List<Answer> answers, Boolean consentAgreed,
		Boolean aiSummaryAgreed, UUID draftResponseId) {

	public SubmitCommand(UUID formId, UUID userId, int formVersion, List<Answer> answers, Boolean consentAgreed,
			Boolean aiSummaryAgreed) {
		this(formId, userId, formVersion, answers, consentAgreed, aiSummaryAgreed, null);
	}

}
