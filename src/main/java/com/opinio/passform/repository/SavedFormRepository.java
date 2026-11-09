package com.opinio.passform.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;

public interface SavedFormRepository extends JpaRepository<SavedForm, SavedFormId> {

	// 내 폼함의 기본 순서다(SPEC 5절 200행): saved_at 내림차순 → form_id 오름차순.
	List<SavedForm> findByUserIdOrderBySavedAtDescFormIdAsc(UUID userId);


	// 폼 삭제 때 내 폼함에서 뺀다(사람 결정, #63).
	@Modifying
	@Query("delete from SavedForm s where s.formId = :formId")
	void deleteByFormId(@Param("formId") UUID formId);

}
