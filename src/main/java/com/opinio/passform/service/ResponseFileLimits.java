package com.opinio.passform.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// 응답 파일의 크기 한도(SPEC 5절 221행). 같은 문항에 붙은 파일의 합계로 판정하고, 응답 전체 합계에는 한도가 없다.
final class ResponseFileLimits {

	static final long PER_QUESTION_BYTES = 25_000_000L;

	private ResponseFileLimits() {
	}

	static void requireWithinQuestionLimit(Map<String, Long> bytesByQuestion) {
		if (bytesByQuestion.values().stream().anyMatch(bytes -> bytes > PER_QUESTION_BYTES)) {
			throw new DomainException(ErrorCode.FILE_TOO_LARGE);
		}
	}

	static void requireWithinQuestionLimit(List<Attachment> kept, List<StoredFile> added) {
		Map<String, Long> bytes = new HashMap<>();
		kept.forEach(file -> bytes.merge(file.getQuestionId(), file.getSizeBytes(), Long::sum));
		added.forEach(file -> bytes.merge(file.questionId(), file.sizeBytes(), Long::sum));
		requireWithinQuestionLimit(bytes);
	}

}
