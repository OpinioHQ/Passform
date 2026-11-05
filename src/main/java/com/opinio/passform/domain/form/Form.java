package com.opinio.passform.domain.form;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 뼈대다. 상태 전이와 버전 규칙은 이 엔티티의 메서드로 E2에서 더한다.
@Entity
@Table(name = "forms")
public class Form {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "form_id")
	private UUID id;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FormStatus status;

	private String slug;

	@Column(name = "definition_version")
	private Integer definitionVersion;

	private Instant deadline;

	@Column(nullable = false)
	private boolean editable;

	@Column(nullable = false)
	private boolean cancellable;

	@Column(name = "response_limit")
	private Integer responseLimit;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "working_definition", columnDefinition = "jsonb")
	private Map<String, Object> workingDefinition;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "ai_summary_notice", columnDefinition = "jsonb")
	private Map<String, Object> aiSummaryNotice;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Form() {
	}

	// 새 폼은 항상 draft이고 첫 배포 전에는 버전이 없다(AC38). 수정·취소 허용의 기본값은 false다(SPEC 5절 226행).
	public Form(UUID ownerId, Map<String, Object> workingDefinition, Instant createdAt) {
		this.ownerId = ownerId;
		this.status = FormStatus.DRAFT;
		this.workingDefinition = workingDefinition;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	// 배포된 적 있고 삭제되지 않은 폼. 초안과 삭제된 폼은 응답자에게 없는 폼이다(AC38).
	public boolean isPublished() {
		return status != FormStatus.DRAFT && deletedAt == null;
	}

	// deadline이 지나면 status가 open이어도 닫힌 것이다. now == deadline도 닫힘이다(AC16).
	public boolean acceptsSubmissionAt(Instant now) {
		return status == FormStatus.OPEN && (deadline == null || now.isBefore(deadline));
	}

	public boolean isResponseLimitReached(long submittedCount) {
		return responseLimit != null && submittedCount >= responseLimit;
	}

	// N번째 제출을 저장하는 처리 안에서 부른다(SPEC 5절 176행).
	public void closeByResponseLimit(Instant now) {
		this.status = FormStatus.CLOSED;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public FormStatus getStatus() {
		return status;
	}

	public String getSlug() {
		return slug;
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

	public Integer getResponseLimit() {
		return responseLimit;
	}

	public Map<String, Object> getWorkingDefinition() {
		return workingDefinition;
	}

	public Map<String, Object> getAiSummaryNotice() {
		return aiSummaryNotice;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
