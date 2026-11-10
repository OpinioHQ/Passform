package com.opinio.passform.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.template.QuestionTemplate;
import com.opinio.passform.repository.QuestionTemplateRepository;

@Service
public class QuestionTemplateService {

	private final QuestionTemplateRepository questionTemplates;

	public QuestionTemplateService(QuestionTemplateRepository questionTemplates) {
		this.questionTemplates = questionTemplates;
	}

	// 빠른 모드의 추천 문항(SPEC 5절 113행, AC71). 고른 대상의 문항과 목적 공통(all) 문항을 template_id 순으로 준다.
	// 목록 밖의 값은 화면이 보내지 않으므로 400 invalid_request로 드러낸다(사람 결정, #58).
	// profile_key는 템플릿에 없다 — 추가한 뒤 제작자가 본인 정보 여부를 확인한다(AC7).
	@Transactional(readOnly = true)
	public List<QuestionTemplateView> list(String purpose, String audience) {
		// List.of의 contains(null)는 예외를 던지므로 빠진 값을 먼저 거른다.
		if (purpose == null || audience == null || !QuestionTemplate.PURPOSES.contains(purpose)
				|| !QuestionTemplate.AUDIENCES.contains(audience)) {
			throw new DomainException(ErrorCode.INVALID_REQUEST);
		}
		return questionTemplates
			.findByPurposeAndAudienceInOrderByIdAsc(purpose, List.of(audience, QuestionTemplate.COMMON_AUDIENCE))
			.stream()
			.map(QuestionTemplateService::viewOf)
			.toList();
	}

	private static QuestionTemplateView viewOf(QuestionTemplate template) {
		List<String> options = template.getOptions() == null ? List.of() : List.copyOf(template.getOptions());
		return new QuestionTemplateView(template.getId(), template.getQuestionNote(), template.getQuestionType(),
				options, template.getInputFormat());
	}

	public record QuestionTemplateView(String templateId, String questionNote, String questionType, List<String> options,
			Map<String, Object> inputFormat) {
	}

}
