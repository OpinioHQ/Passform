package com.opinio.passform.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.attachment.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	// 응답 한 건의 첨부. 제출 이력 상세가 파일 이름을 보인다.
	List<Attachment> findByResponseIdOrderByCreatedAtAscIdAsc(UUID responseId);

	// 원본을 지워도 되는지 본다. 사용 중인 다른 행이 같은 원본을 쓰면 남긴다(SPEC 5절 222행).
	boolean existsByS3KeyAndAvailableTrue(String s3Key);

	List<Attachment> findByResponseIdIn(Collection<UUID> responseIds);

	// 폼 삭제. 응답에 연결되지 않은 제작자 첨부다.
	List<Attachment> findByFormIdAndResponseIdIsNull(UUID formId);

	// 계정의 저장 공간 사용량(SPEC 5절 171~172행, AC65). 폼 정의·답은 JSON의 UTF-8 바이트, 파일은 size_bytes다(사람 결정, #63).
	// - 내가 만든 폼(삭제 제외): 작업 정의와 모든 배포 버전
	// - 그 폼에 받은 제출 응답(제작자 삭제 제외): 답과 첨부. 응답자가 누구든 제작자에게 센다
	// - 내 계정의 작성 중 답. 삭제된 폼의 것은 제외
	// - 제작자 첨부는 정의가 실제로 참조하는 행만 센다. 취소로 연결이 끊긴 행도 response_id가 비어 있기 때문이다
	@Query(nativeQuery = true, value = """
			select cast(
			  coalesce((select sum(octet_length(cast(f.working_definition as text)))
			            from forms f where f.owner_id = :userId and f.deleted_at is null), 0)
			+ coalesce((select sum(octet_length(cast(v.definition as text)))
			            from form_versions v join forms f on f.form_id = v.form_id
			            where f.owner_id = :userId and f.deleted_at is null), 0)
			+ coalesce((select sum(octet_length(cast(r.content as text)))
			            from responses r join forms f on f.form_id = r.form_id
			            where f.owner_id = :userId and f.deleted_at is null
			              and r.status = 'SUBMITTED' and r.creator_deleted_at is null), 0)
			+ coalesce((select sum(octet_length(cast(r.content as text)))
			            from responses r join forms f on f.form_id = r.form_id
			            where r.user_id = :userId and r.status = 'IN_PROGRESS' and f.deleted_at is null), 0)
			+ coalesce((select sum(a.size_bytes)
			            from attachments a
			            join responses r on r.response_id = a.response_id
			            join forms f on f.form_id = r.form_id
			            where f.owner_id = :userId and f.deleted_at is null and a.available
			              and r.status = 'SUBMITTED' and r.creator_deleted_at is null), 0)
			+ coalesce((select sum(a.size_bytes)
			            from attachments a join forms f on f.form_id = a.form_id
			            where f.owner_id = :userId and f.deleted_at is null and a.available and a.response_id is null
			              and (jsonb_path_query_array(f.working_definition, '$.**.attachment_id')
			                     @> to_jsonb(cast(a.attachment_id as text))
			                   or exists (select 1 from form_versions v
			                              where v.form_id = a.form_id
			                                and jsonb_path_query_array(v.definition, '$.**.attachment_id')
			                                      @> to_jsonb(cast(a.attachment_id as text))))), 0)
			as bigint)
			""")
	long usedBytes(@Param("userId") UUID userId);

}
