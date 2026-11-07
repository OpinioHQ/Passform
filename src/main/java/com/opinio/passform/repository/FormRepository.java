package com.opinio.passform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.form.Form;

import jakarta.persistence.LockModeType;

public interface FormRepository extends JpaRepository<Form, UUID> {

	// 제출이 폼 행을 잠가 선착순 인원 확인과 마감을 한 번에 하나씩 처리한다(AC85).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Form f where f.id = :formId")
	Optional<Form> findByIdForUpdate(@Param("formId") UUID formId);

	Optional<Form> findBySlug(String slug);

	// 내가 만든 폼 목록의 순서다(AGENTS 5절). 삭제된 폼은 빠진다.
	List<Form> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdAsc(UUID ownerId);

}
