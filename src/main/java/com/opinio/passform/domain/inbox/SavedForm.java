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

	// 저장 버튼이나 첫 자동 저장으로 처음 들어온 항목(AC17).
	public static SavedForm saved(UUID userId, UUID formId, Instant now) {
		return new SavedForm(userId, formId, SavedFormState.SAVED, now);
	}

	// 저장하지 않았던 폼에 제출해 처음 들어온 항목. saved_at은 제출 시각이다(SPEC 5절 200행).
	public static SavedForm submitted(UUID userId, UUID formId, Instant now) {
		return new SavedForm(userId, formId, SavedFormState.SUBMITTED, now);
	}

	// 제출하면 항목은 그대로 두고 상태만 바꾼다. saved_at은 처음 들어온 시각이라 바꾸지 않는다.
	// 취소로 saved에 돌아가는 규칙은 T10이 이 엔티티에 더한다(AC17).
	public void markSubmitted() {
		this.state = SavedFormState.SUBMITTED;
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
