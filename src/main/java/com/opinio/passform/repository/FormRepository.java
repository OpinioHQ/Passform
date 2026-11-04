package com.opinio.passform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.form.Form;

import jakarta.persistence.LockModeType;

public interface FormRepository extends JpaRepository<Form, String> {

	Optional<Form> findBySlug(String slug);

	List<Form> findByOwnerIdOrderByCreatedAtDescIdAsc(String ownerId);

	// 선착순 인원을 넘지 않도록 제출을 폼 단위로 줄 세운다(SPEC 5절 176행).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select form from Form form where form.id = :id")
	Optional<Form> findByIdForUpdate(@Param("id") String id);

}
