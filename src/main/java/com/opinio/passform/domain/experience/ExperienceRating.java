package com.opinio.passform.domain.experience;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 테스트 기간 한정 사용 경험 평가(SPEC 4절, AC95). 응답 한 건에 한 행이고, 응답 내용·결과 집계와 분리해 둔다.
// 표 원문은 저장하지 않고 해시만 둔다. 응답이 지워지면 DB가 response_id를 null로 바꾼다(V4).
@Entity
@Table(name = "experience_ratings")
public class ExperienceRating {

	// 작성 용이성 4단계(SPEC 4절 60행). 값은 API·DB에 그대로 쓴다.
	public static final Set<String> EASE_VALUES = Set.of("very_hard", "hard", "easy", "very_easy");

	public static final int IMPROVEMENT_LIMIT = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "experience_rating_id")
	private UUID id;

	@Column(name = "response_id")
	private UUID responseId;

	@Column(name = "form_id", nullable = false)
	private UUID formId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "token_issued_at", nullable = false)
	private Instant tokenIssuedAt;

	@Column(name = "rated_at")
	private Instant ratedAt;

	@Column(name = "ease")
	private String ease;

	@Column(name = "improvement")
	private String improvement;

	protected ExperienceRating() {
	}

	private ExperienceRating(UUID responseId, UUID formId, String tokenHash, Instant tokenIssuedAt) {
		this.responseId = responseId;
		this.formId = formId;
		this.tokenHash = tokenHash;
		this.tokenIssuedAt = tokenIssuedAt;
	}

	// 제출 직후 만드는 평가 자리. 평가 값은 비어 있다.
	public static ExperienceRating issued(UUID responseId, UUID formId, String tokenHash, Instant now) {
		return new ExperienceRating(responseId, formId, tokenHash, now);
	}

	// 값 검사. 범위 밖 선택지, 1000자 초과, 두 문항 모두 빈 값은 invalid_experience다.
	// 개선 의견은 앞뒤 공백을 지우고, 비면 없는 것으로 본다. 글자 수는 답의 최대 글자 수와 같은 방식으로 센다.
	public static Values check(String ease, String improvement) {
		String text = improvement == null ? null : improvement.strip();
		if (text != null && text.isEmpty()) {
			text = null;
		}
		boolean badEase = ease != null && !EASE_VALUES.contains(ease);
		boolean tooLong = text != null && text.length() > IMPROVEMENT_LIMIT;
		if (badEase || tooLong || (ease == null && text == null)) {
			throw new DomainException(ErrorCode.INVALID_EXPERIENCE);
		}
		return new Values(ease, text);
	}

	public boolean isRated() {
		return ratedAt != null;
	}

	// 제출 1건당 한 번만 평가한다.
	public void rate(Values values, Instant now) {
		if (isRated()) {
			throw new DomainException(ErrorCode.ALREADY_RATED);
		}
		this.ease = values.ease();
		this.improvement = values.improvement();
		this.ratedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getResponseId() {
		return responseId;
	}

	public UUID getFormId() {
		return formId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getTokenIssuedAt() {
		return tokenIssuedAt;
	}

	public Instant getRatedAt() {
		return ratedAt;
	}

	public String getEase() {
		return ease;
	}

	public String getImprovement() {
		return improvement;
	}

	public record Values(String ease, String improvement) {
	}

}
