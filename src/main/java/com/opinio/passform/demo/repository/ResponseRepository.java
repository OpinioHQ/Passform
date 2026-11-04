package com.opinio.passform.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.demo.domain.response.Response;

public interface ResponseRepository extends JpaRepository<Response, String> {

	List<Response> findByFormIdAndStatus(String formId, ResponseStatus status);

	long countByFormIdAndStatus(String formId, ResponseStatus status);

	List<Response> findByUserIdAndStatusOrderBySubmittedAtDescIdAsc(String userId, ResponseStatus status);

	Optional<Response> findByIdAndFormId(String id, String formId);

}
