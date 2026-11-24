package com.opinio.passform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.experience.ExperienceRating;
import com.opinio.passform.repository.ExperienceRatingRepository;

// 테스트 기간 한정 사용 경험 평가(SPEC 4절 59~61행, AC95).
// 설정 passform.experience-survey.enabled가 꺼져 있으면 표를 만들지 않고 평가도 받지 않는다.
// 표 원문은 제출한 사람에게만 한 번 주고, 서버에는 해시만 남긴다. 로그에도 남기지 않는다.
@Service
public class ExperienceSurveyService {

	private static final Logger log = LoggerFactory.getLogger(ExperienceSurveyService.class);

	private static final int TOKEN_BYTES = 32;

	private final boolean enabled;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	// 저장소 없이 판정만 시험하는 문맥(모의 저장소만 붙인 MockMvc 시험)에서는 주입되지 않는다. 그때는 꺼진 것으로 본다.
	private ExperienceRatingRepository ratings;

	public ExperienceSurveyService(@Value("${passform.experience-survey.enabled:false}") boolean enabled, Clock clock) {
		this.enabled = enabled;
		this.clock = clock;
	}

	@Autowired(required = false)
	public void setRatings(ExperienceRatingRepository ratings) {
		this.ratings = ratings;
	}

	private boolean open() {
		return enabled && ratings != null;
	}

	// 새 응답 제출이 끝난(트랜잭션이 커밋된) 뒤 부른다. 꺼져 있으면 null이다.
	// 만들다 실패해도 제출은 이미 성공했으므로 예외를 던지지 않고 로그만 남긴 뒤 null을 돌려준다(SPEC 4절 61행).
	// 이 메서드에는 트랜잭션을 걸지 않는다. 저장소의 저장이 자기 트랜잭션에서 커밋되므로 실패를 여기서 받을 수 있다.
	public String issueToken(UUID formId, UUID responseId) {
		if (!open()) {
			return null;
		}
		try {
			byte[] bytes = new byte[TOKEN_BYTES];
			random.nextBytes(bytes);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			ratings.saveAndFlush(ExperienceRating.issued(responseId, formId, hash(token), clock.instant()));
			return token;
		}
		catch (RuntimeException e) {
			log.warn("사용 경험 평가 표를 만들지 못해 표 없이 제출 완료를 돌려준다. response_id={}", responseId, e);
			return null;
		}
	}

	// 평가 저장. 판정 순서: 설정 꺼짐 409 → 값 400 → 응답·폼·표 불일치 404 → 이미 평가 409.
	// 없는 응답, 다른 폼의 응답, 틀린 표는 모두 같은 404다. 이미 평가함은 표가 맞을 때만 알린다.
	@Transactional
	public void rate(RateCommand command) {
		if (!open()) {
			throw new DomainException(ErrorCode.SURVEY_CLOSED);
		}
		ExperienceRating.Values values = ExperienceRating.check(command.ease(), command.improvement());
		if (command.formId() == null || command.responseId() == null || command.token() == null) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
		ExperienceRating rating = ratings.findByResponseIdForUpdate(command.responseId())
			.filter(found -> found.getFormId().equals(command.formId()))
			.filter(found -> sameHash(found.getTokenHash(), hash(command.token())))
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		rating.rate(values, clock.instant());
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
		}
	}

	// 비교 시간이 값에 따라 달라지지 않게 한다.
	private static boolean sameHash(String stored, String given) {
		return MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), given.getBytes(StandardCharsets.US_ASCII));
	}

	// 평가 요청. 경로의 ID가 UUID가 아니면 null로 받아 404로 판정한다(설정 꺼짐을 먼저 알리기 위해서다).
	public record RateCommand(UUID formId, UUID responseId, String token, String ease, String improvement) {
	}

}
