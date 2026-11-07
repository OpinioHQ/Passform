package com.opinio.passform.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.repository.ConsentTemplateRepository;

@Service
public class ConsentTemplateService {

	private final ConsentTemplateRepository consentTemplates;

	public ConsentTemplateService(ConsentTemplateRepository consentTemplates) {
		this.consentTemplates = consentTemplates;
	}

	// 빌더의 선택 목록이다(SPEC 5절 114행).
	@Transactional(readOnly = true)
	public List<ConsentTemplateView> list() {
		return consentTemplates.findAll(Sort.by("id"))
			.stream()
			.map(template -> new ConsentTemplateView(template.getId(), template.getPurpose(), template.getRetention()))
			.toList();
	}

	public record ConsentTemplateView(String consentTemplateId, String purpose, String retention) {
	}

}
