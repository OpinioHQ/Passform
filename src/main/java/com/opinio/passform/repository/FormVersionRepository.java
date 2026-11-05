package com.opinio.passform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;

public interface FormVersionRepository extends JpaRepository<FormVersion, FormVersionId> {
}
