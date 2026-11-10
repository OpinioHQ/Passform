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

	// 빠른 모드에서 고르는 값(V3 초기 데이터, SPEC 5절 179행 예시). 목적 공통 문항은 audience가 all이다.
	// anyone("누구나")은 행이 없는 대상이라 목적 공통 문항과 기본 정보만 받는다(사람 결정, #58).
	public static final List<String> PURPOSES = List.of("apply", "survey", "reservation", "quiz");

	public static final List<String> AUDIENCES = List.of("high_school", "university", "worker", "anyone");

	public static final String COMMON = "all";

	// 기본 정보(이름·휴대폰 번호·생년월일)는 purpose·audience가 모두 all이고 어떤 선택에도 함께 추천한다.
	public boolean isBasic() {
		return COMMON.equals(purpose) && COMMON.equals(audience);
	}

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
