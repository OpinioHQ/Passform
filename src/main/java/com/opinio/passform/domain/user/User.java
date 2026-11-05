package com.opinio.passform.domain.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// email은 알림 수신용이며 Profile의 email과 다르다.
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "user_id")
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column(name = "deadline_reminder", nullable = false)
	private boolean deadlineReminder;

	@Column(name = "oauth_provider", nullable = false)
	private String oauthProvider;

	@Column(name = "oauth_subject", nullable = false)
	private String oauthSubject;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected User() {
	}

	// 마감 알림의 기본값은 켬이다(SPEC 5절 101행).
	public User(String name, String email, String oauthProvider, String oauthSubject, Instant createdAt) {
		this.name = name;
		this.email = email;
		this.deadlineReminder = true;
		this.oauthProvider = oauthProvider;
		this.oauthSubject = oauthSubject;
		this.createdAt = createdAt;
	}

	public void changeDeadlineReminder(boolean deadlineReminder) {
		this.deadlineReminder = deadlineReminder;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public boolean isDeadlineReminder() {
		return deadlineReminder;
	}

	public String getOauthProvider() {
		return oauthProvider;
	}

	public String getOauthSubject() {
		return oauthSubject;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
