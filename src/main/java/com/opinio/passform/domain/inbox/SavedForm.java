package com.opinio.passform.domain.inbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

// 내 폼함 항목. has_draft는 저장하지 않고 작성 중 응답이 있는지로 계산한다.
// Persistable: 키를 직접 정하므로, 없으면 save()가 같은 항목을 거부하지 않고 덮어쓴다.
@Entity
@Table(name = "saved_forms")
@IdClass(SavedFormId.class)
public class SavedForm implements Persistable<SavedFormId> {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Id
	@Column(name = "form_id")
	private UUID formId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SavedFormState state;

	@Column(name = "saved_at", nullable = false)
	private Instant savedAt;

	@Transient
	private boolean newEntity = true;

	protected SavedForm() {
	}

	public SavedForm(UUID userId, UUID formId, SavedFormState state, Instant savedAt) {
		this.userId = userId;
		this.formId = formId;
		this.state = state;
		this.savedAt = savedAt;
	}

	@Override
	public SavedFormId getId() {
		return new SavedFormId(userId, formId);
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		newEntity = false;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getFormId() {
		return formId;
	}

	public SavedFormState getState() {
		return state;
	}

	public Instant getSavedAt() {
		return savedAt;
	}

}
