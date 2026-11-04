package com.opinio.passform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.form.FormVersion;

public interface FormVersionRepository extends JpaRepository<FormVersion, String> {

	List<FormVersion> findByFormIdOrderByVersionAsc(String formId);

	Optional<FormVersion> findByFormIdAndVersion(String formId, int version);

}
