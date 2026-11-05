package com.opinio.passform.domain.template;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 추천 문항. 문구는 사람이 쓴다(E13). purpose·audience는 빠른 모드의 제작 목적·응답 대상이고,
// question_type은 form.schema.json의 값(short_answer 등)을 그대로 저장한다.
@Entity
@Table(name = "question_templates")
public class QuestionTemplate {

	@Id
	@Column(name = "template_id")
	private String id;

	@Column(nullable = false)
	private String purpose;

	@Column(nullable = false)
	private String audience;

	@Column(name = "question_note", nullable = false)
	private String questionNote;

	@Column(name = "question_type", nullable = false)
	private String questionType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<String> options;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_format", columnDefinition = "jsonb")
	private Map<String, Object> inputFormat;

	protected QuestionTemplate() {
	}

	public QuestionTemplate(String id, String purpose, String audience, String questionNote, String questionType,
			List<String> options, Map<String, Object> inputFormat) {
		this.id = id;
		this.purpose = purpose;
		this.audience = audience;
		this.questionNote = questionNote;
		this.questionType = questionType;
		this.options = options;
		this.inputFormat = inputFormat;
	}

	public String getId() {
		return id;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getAudience() {
		return audience;
	}

	public String getQuestionNote() {
		return questionNote;
	}

	public String getQuestionType() {
		return questionType;
	}

	public List<String> getOptions() {
		return options;
	}

	public Map<String, Object> getInputFormat() {
		return inputFormat;
	}

}
