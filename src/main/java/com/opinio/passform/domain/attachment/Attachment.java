package com.opinio.passform.domain.attachment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 첨부 메타데이터. 원본은 S3에 있다. response_id가 없으면 제작자 첨부, question_id가 없으면 폼 설명란 첨부다.
@Entity
@Table(name = "attachments")
public class Attachment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "attachment_id")
	private UUID id;

	@Column(name = "form_id", nullable = false)
	private UUID formId;

	@Column(name = "response_id")
	private UUID responseId;

	@Column(name = "question_id")
	private String questionId;

	@Column(nullable = false)
	private String filename;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "s3_key", nullable = false)
	private String s3Key;

	@Column(nullable = false)
	private boolean available;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Attachment() {
	}

	public Attachment(UUID formId, UUID responseId, String questionId, String filename, String contentType,
			long sizeBytes, String s3Key, Instant createdAt) {
		this.formId = formId;
		this.responseId = responseId;
		this.questionId = questionId;
		this.filename = filename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.s3Key = s3Key;
		this.available = true;
		this.createdAt = createdAt;
	}

	// 원본을 지웠다. 행은 남아 이력에 available: false로 보이고 다운로드는 410이다(SPEC 5절 224행).
	public void markRemoved() {
		this.available = false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getFormId() {
		return formId;
	}

	public UUID getResponseId() {
		return responseId;
	}

	public String getQuestionId() {
		return questionId;
	}

	public String getFilename() {
		return filename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getS3Key() {
		return s3Key;
	}

	public boolean isAvailable() {
		return available;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
