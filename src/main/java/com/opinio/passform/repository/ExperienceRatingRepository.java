package com.opinio.passform.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.experience.ExperienceRating;

import jakarta.persistence.LockModeType;

// 사용 경험 평가(AC95). 결과·분석·내보내기는 이 저장소를 읽지 않는다.
public interface ExperienceRatingRepository extends JpaRepository<ExperienceRating, UUID> {

	// 같은 응답의 평가가 동시에 두 번 들어와도 한 번만 저장되도록 행을 잠근다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from ExperienceRating e where e.responseId = :responseId")
	Optional<ExperienceRating> findByResponseIdForUpdate(@Param("responseId") UUID responseId);

}
