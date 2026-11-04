package com.opinio.passform.demo.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.demo.domain.ConflictException;
import com.opinio.passform.demo.domain.NotFoundException;
import com.opinio.passform.demo.domain.form.ConsentTemplate;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.FormJson;
import com.opinio.passform.demo.domain.form.FormVersion;
import com.opinio.passform.demo.domain.form.Question;
import com.opinio.passform.demo.repository.ConsentTemplateRepository;
import com.opinio.passform.demo.repository.FormRepository;
import com.opinio.passform.demo.repository.FormVersionRepository;

import tools.jackson.databind.JsonNode;

// GET /f/{slug}, GET /forms/{form_id} (SPEC 5절 187행, 210~211행)
@Service
public class FormQueryService {

	private static final Map<String, String> CONSENT_ITEMS = Map.of("name", "이름", "mobile_phone", "휴대전화번호",
			"landline_phone", "전화번호", "address", "주소", "student_id", "학번", "email", "이메일", "date", "날짜");

	private static final String DEFAULT_ITEM = "문항별 응답 내용";

	private static final String REFUSAL_NOTICE = "동의를 거부할 수 있으며, 거부하면 이 폼을 제출할 수 없습니다.";

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ConsentTemplateRepository consentTemplates;

	private final Clock clock;

	public FormQueryService(FormRepository forms, FormVersionRepository versions,
			ConsentTemplateRepository consentTemplates, Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.consentTemplates = consentTemplates;
		this.clock = clock;
	}

	public record PublicFormView(String formId, int definitionVersion, String title, String note,
			JsonNode noteAttachments, Instant deadline, JsonNode sections, Display display, JsonNode paymentLink,
			ConsentView consent) {
	}

	public record Display(String defaultMode, List<String> allowedModes) {
	}

	public record ConsentView(List<String> items, String purpose, String retention, String refusalNotice,
			AiSummaryNotice aiSummary) {
	}

	public record AiSummaryNotice(String provider, String processingCountry, String items, String purpose,
			String retention, String refusalEffect, String checkedOn) {
	}

	@Transactional(readOnly = true)
	public PublicFormView bySlug(String slug) {
		return view(forms.findBySlug(slug).orElseThrow(NotFoundException::new));
	}

	@Transactional(readOnly = true)
	public PublicFormView byId(String formId) {
		return view(forms.findById(formId).orElseThrow(NotFoundException::new));
	}

	private PublicFormView view(Form form) {
		if (!form.isPublished()) {
			throw new NotFoundException();
		}
		if (!form.isAcceptingAt(clock.instant())) {
			throw new ConflictException("form_closed");
		}
		FormVersion version = versions.findByFormIdAndVersion(form.getId(), form.getDefinitionVersion())
			.orElseThrow(NotFoundException::new);
		FormDefinition definition = version.definition();
		JsonNode tree = FormJson.parseTree(version.getDefinitionJson());
		Display display = new Display(form.getDefaultDisplayMode(),
				definition.hasBranching() ? List.of(form.getDefaultDisplayMode())
						: List.of("one_question", "all_questions"));
		return new PublicFormView(form.getId(), form.getDefinitionVersion(), definition.title(), definition.note(),
				tree.path("note_attachments"), form.getDeadline(), tree.path("sections"), display,
				tree.get("payment_link"), consent(form, definition));
	}

	private ConsentView consent(Form form, FormDefinition definition) {
		ConsentTemplate template = consentTemplates.findById(form.getConsentTemplateId())
			.orElseThrow(NotFoundException::new);
		Set<String> items = new LinkedHashSet<>();
		for (Question question : definition.questions()) {
			boolean hasFormat = question.inputFormat() != null && question.inputFormat().kind() != null;
			items.add(hasFormat ? CONSENT_ITEMS.getOrDefault(question.inputFormat().kind(), DEFAULT_ITEM) : DEFAULT_ITEM);
		}
		// 고지 문구는 플랫폼이 고정으로 붙인다. 값은 SPEC 5절 211행(확인일 2026-10-28).
		AiSummaryNotice notice = form.isAiSummaryEnabled() ? new AiSummaryNotice("OpenAI",
				"미국(프로젝트 데이터 저장 위치 설정 확인 후 확정)", "AI 요약 대상 주관식 응답 내용(개인정보 문항 제외, 연락처 마스킹)", "제작자의 응답 요약·주제 분류",
				"제공사 정책상 남용 감시 로그를 최대 30일 보관(법적 요구가 있으면 더 길어질 수 있음). 기본 설정에서 학습에 사용하지 않음",
				"제출할 수 있으며, 내 응답은 AI 요약에서만 제외된다", "2026-10-28") : null;
		return new ConsentView(new ArrayList<>(items), template.getPurpose(), template.getRetention(), REFUSAL_NOTICE,
				notice);
	}

}
