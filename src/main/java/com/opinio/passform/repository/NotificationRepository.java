package com.opinio.passform.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.inbox.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	// 같은 사용자·폼·종류에 한 번만 만든다. UNIQUE(user_id, form_id, type)가 최종 방어이고, 동시에 두 번 돌면 늦은 쪽은 0을 받는다.
	// 자기 트랜잭션으로 커밋한 뒤 돌아오므로, 1을 받은 쪽만 메일을 보낸다.
	@Transactional
	@Modifying
	@Query(value = """
			insert into notifications (notification_id, user_id, form_id, type, read, created_at)
			values (:notificationId, :userId, :formId, :type, false, :createdAt)
			on conflict (user_id, form_id, type) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId,
			@Param("formId") UUID formId, @Param("type") String type, @Param("createdAt") Instant createdAt);

	// 알림 순서(AGENTS.md 5절): created_at 내림차순 → id 오름차순.
	List<Notification> findByUserIdOrderByCreatedAtDescIdAsc(UUID userId);

	Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

}
