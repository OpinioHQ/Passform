package com.opinio.passform.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.inbox.NotificationType;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;
import com.opinio.passform.domain.inbox.SavedFormState;

public interface SavedFormRepository extends JpaRepository<SavedForm, SavedFormId> {

	// 내 폼함의 기본 순서다(SPEC 5절 200행): saved_at 내림차순 → form_id 오름차순.
	List<SavedForm> findByUserIdOrderBySavedAtDescFormIdAsc(UUID userId);

	// 폼 삭제 때 내 폼함에서 뺀다(사람 결정, #63).
	@Modifying
	@Query("delete from SavedForm s where s.formId = :formId")
	void deleteByFormId(@Param("formId") UUID formId);

	// 마감 알림 후보(AC18): 저장했지만 제출하지 않은 항목(has_draft와 무관), 알림을 켠 사용자, open이고 삭제되지 않은 폼,
	// 마감이 (now, until] 안, 아직 만들지 않은 알림. 서비스가 엔티티 규칙으로 다시 확인한다.
	@Query("""
			select s.userId as userId, s.formId as formId
			from SavedForm s, Form f, User u
			where s.formId = f.id and s.userId = u.id
			  and s.state = :saved and u.deadlineReminder = true
			  and f.status = :open and f.deletedAt is null
			  and f.deadline > :now and f.deadline <= :until
			  and not exists (select 1 from Notification n
			                  where n.userId = s.userId and n.formId = s.formId and n.type = :type)
			order by f.deadline asc, s.formId asc, s.userId asc
			""")
	List<ReminderTarget> findReminderTargets(@Param("now") Instant now, @Param("until") Instant until,
			@Param("saved") SavedFormState saved, @Param("open") FormStatus open, @Param("type") NotificationType type);

	interface ReminderTarget {

		UUID getUserId();

		UUID getFormId();

	}

}
