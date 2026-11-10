package com.opinio.passform.domain.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "notification_id")
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "form_id", nullable = false)
	private UUID formId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false)
	private boolean read;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Notification() {
	}

	public Notification(UUID userId, UUID formId, NotificationType type, Instant createdAt) {
		this.userId = userId;
		this.formId = formId;
		this.type = type;
		this.read = false;
		this.createdAt = createdAt;
	}

	// 읽음은 되돌리지 않는다(SPEC 5절 202행의 {read: true}).
	public void markRead() {
		this.read = true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getFormId() {
		return formId;
	}

	public NotificationType getType() {
		return type;
	}

	public boolean isRead() {
		return read;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
