package com.opinio.passform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.attachment.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	// 응답 한 건의 첨부. 제출 이력 상세가 파일 이름을 보인다.
	List<Attachment> findByResponseIdOrderByCreatedAtAscIdAsc(UUID responseId);

}
