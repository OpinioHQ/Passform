package com.opinio.passform.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// ui_mode는 화면만 바꾼다. 권한은 Form.owner_id와 소유 관계로 판정한다(AGENTS 2절).
@Entity
@Table(name = "users")
public class User {

	@Id
	@Column(name = "user_id")
	private String id;

	private String name;

	private String email;

	@Column(nullable = false)
	private String uiMode;

	private boolean deadlineReminder;

	protected User() {
	}

	public User(String id, String name, String email) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.uiMode = "respondent";
		this.deadlineReminder = true;
	}

	public void changeUiMode(String uiMode) {
		this.uiMode = uiMode;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getUiMode() {
		return uiMode;
	}

	public boolean isDeadlineReminder() {
		return deadlineReminder;
	}

}
