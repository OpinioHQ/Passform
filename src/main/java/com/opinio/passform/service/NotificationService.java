package com.opinio.passform.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.inbox.Notification;
import com.opinio.passform.repository.NotificationRepository;

// 사이트 내 알림(SPEC 5절 201~202행, AC18). 본인 알림만 다루고 남의 알림은 없는 것과 같다(404).
@Service
public class NotificationService {

	private final NotificationRepository notifications;

	public NotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	// created_at 내림차순 → id 오름차순(AGENTS.md 5절).
	@Transactional(readOnly = true)
	public List<NotificationView> list(UUID userId) {
		return notifications.findByUserIdOrderByCreatedAtDescIdAsc(userId)
			.stream()
			.map(notification -> new NotificationView(notification.getId(), notification.getFormId(),
					notification.getType().name().toLowerCase(Locale.ROOT), notification.getCreatedAt(),
					notification.isRead()))
			.toList();
	}

	@Transactional
	public void markRead(UUID userId, String notificationId) {
		notifications.findByIdAndUserId(FormService.parseId(notificationId), userId)
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND))
			.markRead();
	}

	public record NotificationView(UUID notificationId, UUID formId, String type, Instant createdAt, boolean read) {
	}

}
