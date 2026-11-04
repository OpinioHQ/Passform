package com.opinio.passform.domain.form;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

// 과거 배포 정의는 불변으로 보존한다(SPEC 5절 156행).
@Entity
@Table(name = "form_versions")
public class FormVersion {

	@Id
	private String id;

	@Column(nullable = false)
	private String formId;

	@Column(nullable = false)
	private int version;

	@Column(nullable = false, columnDefinition = "text")
	private String definitionJson;

	@Transient
	private FormDefinition parsed;

	protected FormVersion() {
	}

	public FormVersion(String formId, int version, String definitionJson) {
		this.id = formId + ":" + version;
		this.formId = formId;
		this.version = version;
		this.definitionJson = definitionJson;
	}

	public int getVersion() {
		return version;
	}

	public String getDefinitionJson() {
		return definitionJson;
	}

	public FormDefinition definition() {
		if (parsed == null) {
			parsed = FormJson.parseDefinition(definitionJson);
		}
		return parsed;
	}

}
