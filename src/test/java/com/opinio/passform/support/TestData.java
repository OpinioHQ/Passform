package com.opinio.passform.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.user.User;

// DB 테스트의 가상 데이터. 실제 개인정보를 쓰지 않는다.
public final class TestData {

	public static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

	private TestData() {
	}

	public static User newUser() {
		return new User("가상 사용자", "virtual@example.com", "google", UUID.randomUUID().toString(), NOW);
	}

	public static Response submitted(UUID formId, UUID userId, int version, Instant submittedAt) {
		return new Response(formId, userId, version, ResponseStatus.SUBMITTED, List.of(new Answer("q1", "예")), false,
				null, submittedAt);
	}

	public static Response withStatus(UUID formId, UUID userId, ResponseStatus status) {
		return new Response(formId, userId, 1, status, List.of(), false, NOW, null);
	}

}
