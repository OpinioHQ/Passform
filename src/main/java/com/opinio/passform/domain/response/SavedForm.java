package com.opinio.passform.domain.response;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 내 폼함(온톨로지 User —save→ Form의 구현). 같은 폼은 한 항목만 둔다(AC17).
@Entity
@Table(name = "saved_forms")
public class SavedForm {

	public static final String SAVED = "saved";

	public static final String SUBMITTED = "submitted";

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private String formId;

	@Column(nullable = false)
	private String state;

	protected SavedForm() {
	}

	public SavedForm(String userId, String formId, String state) {
		this.id = userId + ":" + formId;
		this.userId = userId;
		this.formId = formId;
		this.state = state;
	}

	public static String idOf(String userId, String formId) {
		return userId + ":" + formId;
	}

	public void markSubmitted() {
		state = SUBMITTED;
	}

	public String getFormId() {
		return formId;
	}

	public String getState() {
		return state;
	}

}
