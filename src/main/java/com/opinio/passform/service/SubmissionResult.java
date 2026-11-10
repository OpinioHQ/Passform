package com.opinio.passform.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.opinio.passform.service.ResponseHistoryService.AttachmentItem;

// 저장된 제출 응답. content의 문구는 제출 버전의 것이다.
public record SubmissionResult(UUID responseId, int formVersion, List<SubmittedAnswer> content,
		List<AttachmentItem> attachments, Instant submittedAt) {

	public SubmissionResult(UUID responseId, int formVersion, List<SubmittedAnswer> content, Instant submittedAt) {
		this(responseId, formVersion, content, List.of(), submittedAt);
	}

	public record SubmittedAnswer(String questionId, String questionNote, Object value) {
	}

}
