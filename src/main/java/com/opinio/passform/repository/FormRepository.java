package com.opinio.passform.repository;

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

}
