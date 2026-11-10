package com.opinio.passform.domain.form;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

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

	// 초안과 삭제된 폼은 공개 조회에서 없는 폼이다(AC38).
	public boolean isHiddenFromPublic() {
		return status == FormStatus.DRAFT || deletedAt != null;
	}

	// deadline이 지나면 status가 open이어도 닫힌 것이다. now == deadline도 닫힘이다(AC16, AC41).
	public boolean isClosedAt(Instant now) {
		return status == FormStatus.CLOSED || (deadline != null && !now.isBefore(deadline));
	}

	// 마감 알림 대상 폼(AC18): open이고 닫히지 않았으며(마감·선착순 마감 포함) 마감까지 남은 시간이 0보다 크고 window 이하다.
	// deadline이 없거나 삭제된 폼은 대상이 아니다.
	public boolean isDeadlineReminderDue(Instant now, Duration window) {
		return status == FormStatus.OPEN && deletedAt == null && deadline != null && !isClosedAt(now)
				&& !deadline.isAfter(now.plus(window));
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean isOwnedBy(UUID userId) {
		return ownerId.equals(userId);
	}

	// 폼 삭제. 정의는 지우고, 이력이 form_id를 돌려주므로 행은 남긴다(ARCHITECTURE 10절).
	public void markDeleted(Instant now) {
		this.workingDefinition = null;
		this.deletedAt = now;
		this.updatedAt = now;
	}

	// 첫 배포 뒤에는 되돌리거나 새 버전을 배포해도 바꿀 수 없다(SPEC 5절 157행, AC90).
	public void checkAiSummaryChange(boolean current, boolean next) {
		if (definitionVersion != null && current != next) {
			throw new DomainException(ErrorCode.AI_SUMMARY_LOCKED);
		}
	}

	// draft → open, open ↔ closed, 응답이 0건일 때만 open → draft(SPEC 5절 175행, AC57). 같은 상태는 전이가 아니다.
	public void checkTransition(FormStatus target, long submittedCount) {
		boolean allowed = target == status || switch (status) {
			case DRAFT -> target == FormStatus.OPEN;
			case OPEN -> target == FormStatus.CLOSED || (target == FormStatus.DRAFT && submittedCount == 0);
			case CLOSED -> target == FormStatus.OPEN;
		};
		if (!allowed) {
			throw new DomainException(ErrorCode.INVALID_TRANSITION);
		}
	}

	// 다시 open하거나 open 폼의 인원을 바꿀 때 현재 제출 수 이하면 거부한다(SPEC 5절 176행, AC85).
	public void checkResponseLimit(FormStatus target, Integer newLimit, long submittedCount) {
		boolean applies = target == FormStatus.OPEN && (status != FormStatus.OPEN || !Objects.equals(responseLimit, newLimit));
		if (applies && newLimit != null && newLimit <= submittedCount) {
			throw new DomainException(ErrorCode.RESPONSE_LIMIT_REACHED);
		}
	}

	// 작업 정의와 현재 운영 설정을 함께 바꾼다. 운영 설정은 이 컬럼이 정본이다.
	public void replaceDefinition(Map<String, Object> definition, Instant newDeadline, boolean newEditable,
			boolean newCancellable, Integer newResponseLimit, Instant now) {
		this.workingDefinition = definition;
		this.deadline = newDeadline;
		this.editable = newEditable;
		this.cancellable = newCancellable;
		this.responseLimit = newResponseLimit;
		this.updatedAt = now;
	}

	// 전이는 checkTransition을 통과한 뒤에 부른다.
	public void moveTo(FormStatus target) {
		this.status = target;
	}

	// 새 배포 버전을 기록한다. slug와 AI 요약 고지는 처음 정해지면 바꾸지 않는다(SPEC 5절 158행, 175행).
	public void recordPublishedVersion(int version, String newSlug, Map<String, Object> notice) {
		this.definitionVersion = version;
		if (slug == null) {
			this.slug = newSlug;
		}
		if (version == 1) {
			this.aiSummaryNotice = notice;
		}
	}

}
