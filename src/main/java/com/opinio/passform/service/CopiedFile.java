package com.opinio.passform.service;

// 폼 복사에서 새 키로 복사해 둔 첨부 원본. sourceAttachmentId는 원본 폼 정의의 ID다.
public record CopiedFile(String sourceAttachmentId, String questionId, String filename, String contentType,
		long sizeBytes, String key) {
}
