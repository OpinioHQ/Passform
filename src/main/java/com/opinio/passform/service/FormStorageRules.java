package com.opinio.passform.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.opinio.passform.service.ResponseHistoryService.AttachmentItem;

// 폼·응답을 저장할 때 지키는 저장 공간 규칙. 저장소 없이 판정만 시험하는 골든 테스트는 NONE을 쓴다.
public interface FormStorageRules {

	FormStorageRules NONE = new FormStorageRules() {

		@Override
		public long lockAndMeasure(UUID ownerId) {
			return 0;
		}

		@Override
		public void requireWithinLimit(UUID ownerId, long usedBefore) {
		}

		@Override
		public void releaseUnreferenced(UUID formId, Set<String> attachmentIds) {
		}

		@Override
		public List<AttachmentItem> attachToResponse(UUID formId, UUID responseId, List<StoredFile> files, Instant now) {
			return List.of();
		}

	};

	// 소유자 기준으로 직렬화하고 지금 사용량을 돌려준다. 폼 행을 잠그는 경로는 폼 행을 먼저 잠근 뒤 부른다.
	long lockAndMeasure(UUID ownerId);

	// 저장 뒤 사용량이 한도를 넘고 저장 전보다 늘었으면 409다. 정확히 500,000,000바이트는 허용한다.
	void requireWithinLimit(UUID ownerId, long usedBefore);

	// 작업 정의에서 빠진 첨부 중 어느 버전도 참조하지 않는 것의 행을 지우고, 커밋 뒤 원본을 지운다.
	void releaseUnreferenced(UUID formId, Set<String> attachmentIds);

	// 올린 파일을 응답에 연결한다. 호출자의 트랜잭션 안에서 부른다.
	List<AttachmentItem> attachToResponse(UUID formId, UUID responseId, List<StoredFile> files, Instant now);

}
