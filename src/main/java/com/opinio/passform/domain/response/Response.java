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
