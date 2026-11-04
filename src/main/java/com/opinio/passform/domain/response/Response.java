package com.opinio.passform.domain.response;

import java.time.Instant;

import com.opinio.passform.domain.analysis.ResponseStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "responses", indexes = { @Index(columnList = "formId,status"), @Index(columnList = "userId,status") })
public class Response {

	@Id
	@Column(name = "response_id")
	private String id;

	@Column(nullable = false)
	private String formId;

	private String userId;

	@Column(nullable = false)
	private int formVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ResponseStatus status;

	@Column(nullable = false, columnDefinition = "text")
	private String answersJson;

	private boolean aiSummaryAgreed;

	private Instant submittedAt;

	private Instant updatedAt;

	protected Response() {
	}

	public Response(String id, String formId, String userId, int formVersion, ResponseStatus status,
			String answersJson, boolean aiSummaryAgreed, Instant submittedAt, Instant updatedAt) {
		this.id = id;
		this.formId = formId;
		this.userId = userId;
		this.formVersion = formVersion;
		this.status = status;
		this.answersJson = answersJson;
		this.aiSummaryAgreed = aiSummaryAgreed;
		this.submittedAt = submittedAt;
		this.updatedAt = updatedAt;
	}

	public String getId() {
		return id;
	}

	public String getFormId() {
		return formId;
	}

	public String getUserId() {
		return userId;
	}

	public int getFormVersion() {
		return formVersion;
	}

	public ResponseStatus getStatus() {
		return status;
	}

	public String getAnswersJson() {
		return answersJson;
	}

	public boolean isAiSummaryAgreed() {
		return aiSummaryAgreed;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
