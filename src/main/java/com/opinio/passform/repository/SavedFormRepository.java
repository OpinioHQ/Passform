package com.opinio.passform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;

public interface SavedFormRepository extends JpaRepository<SavedForm, SavedFormId> {

	// 내 폼함의 기본 순서다(SPEC 5절 200행): saved_at 내림차순 → form_id 오름차순.
	List<SavedForm> findByUserIdOrderBySavedAtDescFormIdAsc(UUID userId);

}
