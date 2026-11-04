package com.opinio.passform.demo.domain.form;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consent_templates")
public class ConsentTemplate {

	@Id
	@Column(name = "consent_template_id")
	private String id;

	private String purpose;

	private String retention;

	protected ConsentTemplate() {
	}

	public ConsentTemplate(String id, String purpose, String retention) {
		this.id = id;
		this.purpose = purpose;
		this.retention = retention;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getRetention() {
		return retention;
	}

}
