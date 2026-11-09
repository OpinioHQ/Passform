package com.opinio.passform.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.response.Response;

import jakarta.persistence.LockModeType;

public interface ResponseRepository extends JpaRepository<Response, UUID> {

	// 결과·분석·내보내기가 응답을 읽는 한 곳이다(AC20). submitted이면서 제작자가 삭제하지 않은 응답만 돌려준다.
	// 순서는 버전 → 제출 시각 → ID다. 분석 입력은 Response.toResponseRecord()로 바꾼다.
	@Query("""
			select r from Response r
			where r.formId = :formId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			  and r.creatorDeletedAt is null
			order by r.formVersion, r.submittedAt, r.id
			""")
	List<Response> findSubmittedNotDeletedByFormId(@Param("formId") UUID formId);

	// 선착순 인원과 비교하는 현재 제출 응답 수다(SPEC 5절 176행). 위 메서드와 같은 범위를 센다.
	@Query("""
			select count(r) from Response r
			where r.formId = :formId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			  and r.creatorDeletedAt is null
			""")
	long countSubmittedNotDeleted(@Param("formId") UUID formId);

	// 내가 만든 폼 목록의 response_count다(SPEC 5절 120행). 응답이 없는 폼은 결과에 없다.
	@Query("""
			select r.formId as formId, count(r) as responseCount from Response r
			where r.formId in :formIds
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			  and r.creatorDeletedAt is null
			group by r.formId
			""")
	List<FormResponseCount> countSubmittedNotDeletedByFormIds(@Param("formIds") Collection<UUID> formIds);

	// 로그인 사용자의 계정 초안. 폼당 하나다(V1 부분 유일 인덱스 uq_responses_login_draft, SPEC 5절 206행).
	@Query("""
			select r from Response r
			where r.userId = :userId
			  and r.formId = :formId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.IN_PROGRESS
			""")
	Optional<Response> findDraft(@Param("userId") UUID userId, @Param("formId") UUID formId);

	// 초안을 바꾸거나 제출할 때 같은 행을 한 번에 하나씩 다룬다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select r from Response r
			where r.userId = :userId
			  and r.formId = :formId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.IN_PROGRESS
			""")
	Optional<Response> findDraftForUpdate(@Param("userId") UUID userId, @Param("formId") UUID formId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Response r where r.id = :responseId")
	Optional<Response> findByIdForUpdate(@Param("responseId") UUID responseId);

	// 내 폼함 has_draft의 근거다(SPEC 5절 200행). E5의 GET /me/forms가 쓴다.
	@Query("""
			select count(r) > 0 from Response r
			where r.userId = :userId
			  and r.formId = :formId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.IN_PROGRESS
			""")
	boolean hasDraft(@Param("userId") UUID userId, @Param("formId") UUID formId);

	// 제출 이력(SPEC 5절 192행). 본인의 submitted 응답만, submitted_at 내림차순 → response_id 오름차순(AGENTS 5절).
	// 제작자가 지운 응답도 조회 전용 이력으로 남는다(SPEC 5절 224행).
	@Query("""
			select r from Response r
			where r.userId = :userId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			order by r.submittedAt desc, r.id
			""")
	List<Response> findSubmittedByUserId(@Param("userId") UUID userId);

	// 수정·취소할 본인 제출 응답의 폼. 폼 행을 먼저 잠그려고 응답 엔티티를 읽지 않고 ID만 구한다.
	@Query("""
			select r.formId from Response r
			where r.id = :responseId
			  and r.userId = :userId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			""")
	Optional<UUID> findFormIdOfSubmitted(@Param("responseId") UUID responseId, @Param("userId") UUID userId);

	// 취소 뒤 남은 본인 제출 기록이 있는지(AC17). 제작자가 지운 조회 전용 이력도 센다.
	@Query("""
			select count(r) > 0 from Response r
			where r.userId = :userId
			  and r.formId = :formId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			""")
	boolean existsSubmittedByUser(@Param("userId") UUID userId, @Param("formId") UUID formId);

	// 내 폼함 has_draft를 한 번에 구한다(SPEC 5절 200행).
	@Query("""
			select distinct r.formId from Response r
			where r.userId = :userId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.IN_PROGRESS
			""")
	List<UUID> findDraftFormIds(@Param("userId") UUID userId);

	// 내 폼함 submitted_at: 폼마다 본인 제출 기록 중 가장 최근 submitted_at(SPEC 5절 200행).
	@Query("""
			select r.formId as formId, max(r.submittedAt) as submittedAt from Response r
			where r.userId = :userId
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			group by r.formId
			""")
	List<LatestSubmission> findLatestSubmissions(@Param("userId") UUID userId);

	// 과거·마감 버전의 폼 첨부를 볼 수 있는지 판정한다(SPEC 5절 196~197행). 그 버전에 제출한 본인 응답이 있어야 한다.
	@Query("""
			select count(r) > 0 from Response r
			where r.userId = :userId
			  and r.formId = :formId
			  and r.formVersion in :versions
			  and r.status = com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			""")
	boolean existsSubmittedInVersions(@Param("userId") UUID userId, @Param("formId") UUID formId,
			@Param("versions") Collection<Integer> versions);

	// 폼 삭제 때 함께 지우는 제출되지 않은 응답이다(SPEC 5절 208행).
	@Query("""
			select r from Response r
			where r.formId = :formId
			  and r.status <> com.opinio.passform.domain.analysis.ResponseStatus.SUBMITTED
			""")
	List<Response> findNotSubmittedByFormId(@Param("formId") UUID formId);

	interface LatestSubmission {

		UUID getFormId();

		Instant getSubmittedAt();

	}

	interface FormResponseCount {

		UUID getFormId();

		long getResponseCount();

	}

}
