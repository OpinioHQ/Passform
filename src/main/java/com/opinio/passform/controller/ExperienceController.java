package com.opinio.passform.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.ExperienceSurveyService;
import com.opinio.passform.service.ExperienceSurveyService.RateCommand;

// 테스트 기간 한정 사용 경험 평가 저장(SPEC 4절 61행, AC95). 공개 경로이고 CSRF는 켠 채다.
// 값 검사(범위·길이·둘 다 빈 값)는 invalid_experience로 알려야 해서 Bean Validation이 아니라 도메인에서 한다.
@RestController
public class ExperienceController {

	private final ExperienceSurveyService experience;

	public ExperienceController(ExperienceSurveyService experience) {
		this.experience = experience;
	}

	@PostMapping("/forms/{formId}/responses/{responseId}/experience")
	@ResponseStatus(HttpStatus.CREATED)
	public void rate(@PathVariable String formId, @PathVariable String responseId,
			@RequestBody ExperienceRequest request) {
		experience.rate(new RateCommand(uuidOrNull(formId), uuidOrNull(responseId), request.token(), request.ease(),
				request.improvement()));
	}

	// UUID가 아닌 ID의 응답은 없다. 설정 꺼짐(409)을 먼저 알리도록 여기서 404를 던지지 않고 service에 맡긴다.
	private static UUID uuidOrNull(String id) {
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	public record ExperienceRequest(String token, String ease, String improvement) {
	}

}
