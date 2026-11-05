package com.opinio.passform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.response.Response;

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

}
