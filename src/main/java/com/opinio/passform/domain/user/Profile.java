package com.opinio.passform.domain.user;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 뼈대다. personal_info 허용 키 검증(AC3)은 프로필 이슈에서 더한다.
@Entity
@Table(name = "profiles")
public class Profile {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "profile_id")
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private String label;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "personal_info", nullable = false, columnDefinition = "jsonb")
	private Map<String, String> personalInfo;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Profile() {
	}

	public Profile(UUID userId, String label, Map<String, String> personalInfo, Instant createdAt) {
		this.userId = userId;
		this.label = label;
		this.personalInfo = personalInfo;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getLabel() {
		return label;
	}

	public Map<String, String> getPersonalInfo() {
		return personalInfo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
