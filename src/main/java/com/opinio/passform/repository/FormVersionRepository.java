package com.opinio.passform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;

public interface FormVersionRepository extends JpaRepository<FormVersion, FormVersionId> {

	// 첨부가 어느 버전에서 참조되는지 볼 때 쓴다.
	List<FormVersion> findByFormId(UUID formId);

}
