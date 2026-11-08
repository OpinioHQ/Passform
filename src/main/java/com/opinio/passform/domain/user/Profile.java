package com.opinio.passform.domain.user;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profiles")
public class Profile {

	private static final int LABEL_MAX_CHARS = 30;

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

	// 입력을 모두 검사한 뒤 만든다. 허용 키 검사가 먼저다(AC3).
	public static Profile create(UUID userId, Object label, Map<String, Object> personalInfo, Instant now) {
		Map<String, String> values = PersonalInfo.validated(personalInfo);
		return new Profile(userId, validLabel(label), values, now);
	}

	// PUT은 통째로 바꾼다. 검사에 실패하면 아무것도 바꾸지 않는다.
	public void replace(Object label, Map<String, Object> personalInfo) {
		Map<String, String> values = PersonalInfo.validated(personalInfo);
		this.label = validLabel(label);
		this.personalInfo = values;
	}

	// 사람이 정한 것(2026-11-08): 앞뒤 공백을 지운 뒤 1~30자. 같은 이름은 허용한다.
	private static String validLabel(Object label) {
		if (!(label instanceof String text) || text.isBlank() || text.indexOf('\0') >= 0) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		String stripped = text.strip();
		if (stripped.codePointCount(0, stripped.length()) > LABEL_MAX_CHARS) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		return stripped;
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
