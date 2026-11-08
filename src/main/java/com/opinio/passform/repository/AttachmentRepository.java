package com.opinio.passform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.attachment.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	// 응답 한 건의 첨부. 제출 이력 상세가 파일 이름을 보인다.
	List<Attachment> findByResponseIdOrderByCreatedAtAscIdAsc(UUID responseId);

	// 응답을 취소하면 연결을 끊는다. 원본 정리는 T12가 s3_key로 한다(SPEC 5절 222행).
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("update Attachment a set a.responseId = null where a.responseId = :responseId")
	int detachFromResponse(@Param("responseId") UUID responseId);

}
