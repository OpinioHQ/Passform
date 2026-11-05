package com.opinio.passform.domain.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 문구는 사람이 쓴다(E2). ID는 시드의 consent_default처럼 읽을 수 있는 문자열이다.
@Entity
@Table(name = "consent_templates")
public class ConsentTemplate {

	@Id
	@Column(name = "consent_template_id")
	private String id;

	@Column(nullable = false)
	private String purpose;

	@Column(nullable = false)
	private String retention;

	protected ConsentTemplate() {
	}

	public ConsentTemplate(String id, String purpose, String retention) {
		this.id = id;
		this.purpose = purpose;
		this.retention = retention;
	}

	public String getId() {
		return id;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getRetention() {
		return retention;
	}

}
