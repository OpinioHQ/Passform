package com.opinio.passform.domain.response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 뼈대다. 제출·수정·취소·제작자 삭제 규칙은 이 엔티티의 메서드로 T3, T12에서 더한다.
@Entity
@Table(name = "responses")
public class Response {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "response_id")
	private UUID id;

	@Column(name = "form_id", nullable = false)
	private UUID formId;

	// 비로그인 응답은 null이다.
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "form_version", nullable = false)
	private int formVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ResponseStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<Answer> content;

	@Column(name = "ai_summary_agreed", nullable = false)
	private boolean aiSummaryAgreed;

	@Column(name = "saved_at")
	private Instant savedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Column(name = "creator_deleted_at")
	private Instant creatorDeletedAt;

	@Column(name = "history_form_title")
	private String historyFormTitle;

	// 삭제 뒤 이력에 쓸 문항 문구. 항목은 {question_id, question_note}다. T12가 삭제할 때 채운다.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "history_questions", columnDefinition = "jsonb")
	private List<Map<String, Object>> historyQuestions;

	protected Response() {
	}

	public Response(UUID formId, UUID userId, int formVersion, ResponseStatus status, List<Answer> content,
			boolean aiSummaryAgreed, Instant savedAt, Instant submittedAt) {
		this.formId = formId;
		this.userId = userId;
		this.formVersion = formVersion;
		this.status = status;
		this.content = content;
		this.aiSummaryAgreed = aiSummaryAgreed;
		this.savedAt = savedAt;
		this.submittedAt = submittedAt;
	}

	// 제출 응답. saved_at은 작성 중 임시 저장 시각이라 초안 없이 제출하면 null이다.
	public static Response submitted(UUID formId, UUID userId, int formVersion, List<Answer> content,
			boolean aiSummaryAgreed, Instant submittedAt) {
		return new Response(formId, userId, formVersion, ResponseStatus.SUBMITTED, content, aiSummaryAgreed, null,
				submittedAt);
	}

	// 로그인 사용자의 작성 중 응답(SPEC 5절 205~207행). 제출 검증을 하지 않고, 동의·제출로 처리하지 않는다.
	public static Response draft(UUID formId, UUID userId, int formVersion, List<Answer> content, Instant savedAt) {
		if (userId == null) {
			throw new IllegalArgumentException("계정 초안은 로그인 사용자만 만든다");
		}
		return new Response(formId, userId, formVersion, ResponseStatus.IN_PROGRESS, content, false, savedAt, null);
	}

	public boolean isDraftOf(UUID userId, UUID formId) {
		return status == ResponseStatus.IN_PROGRESS && this.userId != null && this.userId.equals(userId)
				&& this.formId.equals(formId);
	}

	// 같은 초안의 답을 통째로 바꾼다. 작성 시작 버전은 바꾸지 않는다(SPEC 5절 208행).
	public void replaceDraftAnswers(List<Answer> answers, Instant now) {
		requireInProgress();
		this.content = answers;
		this.savedAt = now;
	}

	// 같은 response_id를 submitted로 바꾼다(AC83). 검증은 제출 경로가 끝낸 뒤 부른다.
	public void submitDraft(List<Answer> answers, boolean aiSummaryAgreed, Instant now) {
		requireInProgress();
		this.status = ResponseStatus.SUBMITTED;
		this.content = answers;
		this.aiSummaryAgreed = aiSummaryAgreed;
		this.submittedAt = now;
	}

	// 제출 후 수정. 같은 응답의 답을 통째로 바꾸고 수정 시각만 기록한다. response_id·form_version·submitted_at·동의는 그대로다(AC74).
	public void replaceSubmittedAnswers(List<Answer> answers, Instant now) {
		if (status != ResponseStatus.SUBMITTED) {
			throw new IllegalStateException("제출된 응답이 아니다");
		}
		this.content = answers;
		this.updatedAt = now;
	}

	private void requireInProgress() {
		if (status != ResponseStatus.IN_PROGRESS) {
			throw new IllegalStateException("작성 중 응답이 아니다");
		}
	}

	// 분석 입력으로 변환한다. 단일 값도 1개짜리 목록이고 비운 문항은 키를 두지 않는다(ResponseRecord 주석).
	public ResponseRecord toResponseRecord() {
		Map<String, List<String>> answers = new LinkedHashMap<>();
		for (Answer answer : content) {
			List<String> values = answer.values();
			if (!values.isEmpty()) {
				answers.put(answer.questionId(), values);
			}
		}
		return new ResponseRecord(id.toString(), formVersion, status, answers);
	}

	public UUID getId() {
		return id;
	}

	public UUID getFormId() {
		return formId;
	}

	public UUID getUserId() {
		return userId;
	}

	public int getFormVersion() {
		return formVersion;
	}

	public ResponseStatus getStatus() {
		return status;
	}

	public List<Answer> getContent() {
		return content;
	}

	public boolean isAiSummaryAgreed() {
		return aiSummaryAgreed;
	}

	public Instant getSavedAt() {
		return savedAt;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCreatorDeletedAt() {
		return creatorDeletedAt;
	}

	public String getHistoryFormTitle() {
		return historyFormTitle;
	}

	public List<Map<String, Object>> getHistoryQuestions() {
		return historyQuestions;
	}

}
