package com.opinio.passform.domain.form;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "forms")
public class Form {

	@Id
	@Column(name = "form_id")
	private String id;

	@Column(nullable = false)
	private String ownerId;

	@Column(unique = true)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FormStatus status;

	private String title;

	private Integer definitionVersion;

	private Instant deadline;

	private Integer responseLimit;

	private boolean editable;

	private boolean cancellable;

	private boolean aiSummaryEnabled;

	private String consentTemplateId;

	private String defaultDisplayMode;

	@Column(nullable = false)
	private Instant createdAt;

	protected Form() {
	}

	public Form(String id, String ownerId, String slug, FormStatus status, String title, Integer definitionVersion,
			Instant deadline, Integer responseLimit, boolean editable, boolean cancellable, boolean aiSummaryEnabled,
			String consentTemplateId, String defaultDisplayMode, Instant createdAt) {
		this.id = id;
		this.ownerId = ownerId;
		this.slug = slug;
		this.status = status;
		this.title = title;
		this.definitionVersion = definitionVersion;
		this.deadline = deadline;
		this.responseLimit = responseLimit;
		this.editable = editable;
		this.cancellable = cancellable;
		this.aiSummaryEnabled = aiSummaryEnabled;
		this.consentTemplateId = consentTemplateId;
		this.defaultDisplayMode = defaultDisplayMode;
		this.createdAt = createdAt;
	}

	public boolean isOwnedBy(String userId) {
		return ownerId.equals(userId);
	}

	public boolean isPublished() {
		return status != FormStatus.DRAFT;
	}

	// deadline이 지난 폼은 status가 open이어도 닫힌 것으로 처리한다(AGENTS 2절).
	public boolean isAcceptingAt(Instant now) {
		return status == FormStatus.OPEN && (deadline == null || now.isBefore(deadline));
	}

	// 선착순 인원에 도달하는 제출이 성공하면 같은 처리 안에서 닫는다(SPEC 5절 176행).
	public void closeIfLimitReached(long submittedCount) {
		if (responseLimit != null && submittedCount >= responseLimit) {
			status = FormStatus.CLOSED;
		}
	}

	public String getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getSlug() {
		return slug;
	}

	public FormStatus getStatus() {
		return status;
	}

	public String getTitle() {
		return title;
	}

	public Integer getDefinitionVersion() {
		return definitionVersion;
	}

	public Instant getDeadline() {
		return deadline;
	}

	public boolean isEditable() {
		return editable;
	}

	public boolean isCancellable() {
		return cancellable;
	}

	public boolean isAiSummaryEnabled() {
		return aiSummaryEnabled;
	}

	public String getConsentTemplateId() {
		return consentTemplateId;
	}

	public String getDefaultDisplayMode() {
		return defaultDisplayMode;
	}

}
