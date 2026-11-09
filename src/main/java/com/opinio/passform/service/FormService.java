package com.opinio.passform.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormAttachments;
import com.opinio.passform.domain.form.FormPublishValidator;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.VersionTarget;
import com.opinio.passform.domain.template.ConsentNotices;
import com.opinio.passform.repository.ConsentTemplateRepository;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.ResponseRepository.FormResponseCount;
import com.opinio.passform.service.FormSchemaValidator.ValidatedDefinition;

// 폼 저장·배포·버전(SPEC 5절 115~120행, 152~176행).
// 판정 순서: 소유 → 형식 → AI 요약 잠금 → 전이 → 의미 → 인원. 모두 통과한 뒤에만 엔티티를 바꾸므로 실패한 요청은 아무것도 바꾸지 않는다.
@Service
public class FormService {

	private static final int SLUG_BYTES = 16;

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final ConsentTemplateRepository consentTemplates;

	private final FormSchemaValidator schemaValidator;

	private final ShareUrls shareUrls;

	private final Clock clock;

	private final FormStorageRules storageRules;

	private final SecureRandom random = new SecureRandom();

	// 저장 공간 규칙 없이 판정만 시험하는 골든 테스트가 쓴다.
	public FormService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			ConsentTemplateRepository consentTemplates, FormSchemaValidator schemaValidator, ShareUrls shareUrls,
			Clock clock) {
		this(forms, versions, responses, consentTemplates, schemaValidator, shareUrls, clock, FormStorageRules.NONE);
	}

	@Autowired
	public FormService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			ConsentTemplateRepository consentTemplates, FormSchemaValidator schemaValidator, ShareUrls shareUrls,
			Clock clock, FormStorageRules storageRules) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.consentTemplates = consentTemplates;
		this.schemaValidator = schemaValidator;
		this.shareUrls = shareUrls;
		this.clock = clock;
		this.storageRules = storageRules;
	}

	// 새 폼은 항상 draft다(AC38). 형식 검증만 한다(AC56). 첨부는 폼이 생긴 뒤 첨부 API로만 붙는다.
	@Transactional
	public CreatedForm create(CreateCommand command) {
		long usedBefore = storageRules.lockAndMeasure(command.ownerId());
		Object body = command.body() instanceof Map<?, ?> ? FormAttachments.keepStoredLists(objectOf(command.body()), null)
				: command.body();
		ValidatedDefinition definition = schemaValidator.validate(body);
		Instant now = clock.instant();
		Form form = new Form(command.ownerId(), definition.definition(), now);
		form.replaceDefinition(definition.definition(), definition.deadline(), definition.editable(),
				definition.cancellable(), definition.responseLimit(), now);
		Form saved = forms.save(form);
		storageRules.requireWithinLimit(command.ownerId(), usedBefore);
		return new CreatedForm(saved.getId(), saved.getStatus().value());
	}

	// 첨부 API가 호출자의 트랜잭션 안에서 부른다. 호출자가 폼 행을 잠갔다.
	// 초안은 작업 정의만 바꾸고, 배포된 폼은 PATCH와 같은 검증을 거쳐 새 불변 버전을 함께 저장한다(SPEC 5절 156행, 169행).
	@Transactional
	public void replaceAttachmentLists(Form form, Map<String, Object> definition) {
		ValidatedDefinition next = schemaValidator.validate(definition);
		boolean publishing = form.getStatus() != FormStatus.DRAFT;
		if (publishing) {
			FormPublishValidator.validate(next.definition());
			requireConsentTemplate(next.definition());
		}
		Instant now = clock.instant();
		form.replaceDefinition(next.definition(), next.deadline(), next.editable(), next.cancellable(),
				next.responseLimit(), now);
		if (publishing) {
			publishIfChanged(form, next, now);
		}
	}

	@Transactional(readOnly = true)
	public DefinitionView getDefinition(UUID userId, String formId) {
		Form form = forms.findById(parseId(formId)).orElseThrow(FormService::notFound);
		requireOwner(form, userId);
		return new DefinitionView(form.getWorkingDefinition(), form.getStatus().value(), shareUrls.of(form.getSlug()),
				form.getDefinitionVersion());
	}

	// 폼 행을 잠가 제출의 인원 확인, 동시 배포와 한 번에 하나씩 처리한다.
	@Transactional
	public StateView update(UpdateCommand command) {
		Form form = forms.findByIdForUpdate(parseId(command.formId())).orElseThrow(FormService::notFound);
		requireOwner(form, command.userId());
		// 잠금 순서는 폼 행 → 소유자 행이다.
		long usedBefore = storageRules.lockAndMeasure(form.getOwnerId());

		Map<String, Object> changes = objectOf(command.body());
		FormStatus target = changes.containsKey("status") ? parseStatus(changes.remove("status")) : form.getStatus();
		// PATCH는 보낸 최상위 키를 통째로 바꾸고 바꾼 뒤의 전체 정의로 검증한다(SPEC 5절 152행).
		// 첨부 목록만은 첨부 API의 결과를 유지한다. 빌더가 오래된 목록을 보내도 덮어쓰지 않는다.
		Map<String, Object> merged = new LinkedHashMap<>(form.getWorkingDefinition());
		merged.putAll(changes);
		merged = FormAttachments.keepStoredLists(merged, form.getWorkingDefinition());
		ValidatedDefinition next = schemaValidator.validate(merged);
		Set<String> removedAttachments = new HashSet<>(FormAttachments.allIds(form.getWorkingDefinition()));
		removedAttachments.removeAll(FormAttachments.allIds(next.definition()));

		form.checkAiSummaryChange(FormSchemaValidator.aiSummaryEnabled(form.getWorkingDefinition()),
				next.aiSummaryEnabled());
		boolean countNeeded = target != form.getStatus()
				|| !Objects.equals(form.getResponseLimit(), next.responseLimit());
		long submittedCount = countNeeded ? responses.countSubmittedNotDeleted(form.getId()) : 0;
		form.checkTransition(target, submittedCount);

		// draft로 남거나 되돌리면 미완성이어도 저장한다. 그 밖에는 배포본이 되므로 의미 검증을 한다(SPEC 5절 174행).
		boolean publishing = target != FormStatus.DRAFT;
		if (publishing) {
			FormPublishValidator.validate(next.definition());
			requireConsentTemplate(next.definition());
		}
		form.checkResponseLimit(target, next.responseLimit(), submittedCount);

		Instant now = clock.instant();
		form.replaceDefinition(next.definition(), next.deadline(), next.editable(), next.cancellable(),
				next.responseLimit(), now);
		form.moveTo(target);
		if (publishing) {
			publishIfChanged(form, next, now);
		}
		// 문항을 지워 정의에서 빠진 첨부를 정리한다. 새 버전을 저장한 뒤라 과거 버전 참조까지 본다.
		storageRules.releaseUnreferenced(form.getId(), removedAttachments);
		storageRules.requireWithinLimit(form.getOwnerId(), usedBefore);
		return new StateView(form.getStatus().value(), shareUrls.of(form.getSlug()), form.getDefinitionVersion());
	}

	// 내가 만든 폼만 돌려준다(AC39). 응답 수는 Result.response_count와 같은 범위다.
	@Transactional(readOnly = true)
	public List<CreatedFormView> listCreated(UUID ownerId) {
		List<Form> owned = forms.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdAsc(ownerId);
		if (owned.isEmpty()) {
			return List.of();
		}
		Map<UUID, Long> counts = responses.countSubmittedNotDeletedByFormIds(owned.stream().map(Form::getId).toList())
			.stream()
			.collect(Collectors.toMap(FormResponseCount::getFormId, FormResponseCount::getResponseCount));
		return owned.stream()
			.map(form -> new CreatedFormView(form.getId(), titleOf(form), form.getStatus().value(), form.getDeadline(),
					shareUrls.of(form.getSlug()), counts.getOrDefault(form.getId(), 0L)))
			.toList();
	}

	// 버전 대상 정의가 마지막 배포본과 다를 때만 다음 번호로 저장한다(SPEC 5절 155~156행, AC56, AC58).
	private void publishIfChanged(Form form, ValidatedDefinition next, Instant now) {
		Integer current = form.getDefinitionVersion();
		if (current != null && VersionTarget.same(next.definition(), latestDefinition(form))) {
			return;
		}
		int version = current == null ? 1 : current + 1;
		versions.save(new FormVersion(form.getId(), version, new LinkedHashMap<>(next.definition()), now));
		// 고지는 첫 배포 때 AI 요약을 켠 폼에만 기록한다(SPEC 5절 158행).
		form.recordPublishedVersion(version, newSlug(), next.aiSummaryEnabled() ? ConsentNotices.aiSummaryNotice() : null);
	}

	private Map<String, Object> latestDefinition(Form form) {
		return versions.findById(new FormVersionId(form.getId(), form.getDefinitionVersion()))
			.orElseThrow(() -> new IllegalStateException("배포 버전의 정의가 없다"))
			.getDefinition();
	}

	// 없는 템플릿 ID는 없는 것과 같다. 공개 조회가 문구를 찾지 못하기 때문이다(AC12).
	private void requireConsentTemplate(Map<String, Object> definition) {
		boolean known = definition.get("consent_template_id") instanceof String id && consentTemplates.existsById(id);
		if (!known) {
			throw new DomainException(ErrorCode.CONSENT_TEMPLATE_REQUIRED);
		}
	}

	private String newSlug() {
		byte[] bytes = new byte[SLUG_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static void requireOwner(Form form, UUID userId) {
		if (form.isDeleted()) {
			throw notFound();
		}
		// 제작자 전용을 다른 사용자가 부르면 403이다(SPEC 5절 90행).
		if (!form.isOwnedBy(userId)) {
			throw new DomainException(ErrorCode.FORBIDDEN);
		}
	}

	private static String titleOf(Form form) {
		return Optional.ofNullable(form.getWorkingDefinition())
			.map(definition -> definition.get("title"))
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.orElse("");
	}

	// JSON 객체가 아니면 형식 오류다. 넘겨받은 값을 바꾸지 않도록 복사한다.
	@SuppressWarnings("unchecked")
	private static Map<String, Object> objectOf(Object body) {
		if (!(body instanceof Map<?, ?>)) {
			throw new DomainException(ErrorCode.SCHEMA_INVALID);
		}
		return new LinkedHashMap<>((Map<String, Object>) body);
	}

	// 없는 상태 값은 전이 위반이 아니라 형식 오류다.
	private static FormStatus parseStatus(Object value) {
		for (FormStatus status : FormStatus.values()) {
			if (status.value().equals(value)) {
				return status;
			}
		}
		throw new DomainException(ErrorCode.SCHEMA_INVALID);
	}

	// UUID가 아닌 ID는 없는 폼이다.
	static UUID parseId(String formId) {
		try {
			return UUID.fromString(formId);
		}
		catch (IllegalArgumentException e) {
			throw notFound();
		}
	}

	private static DomainException notFound() {
		return new DomainException(ErrorCode.NOT_FOUND);
	}

	public record CreateCommand(UUID ownerId, Object body) {
	}

	public record UpdateCommand(UUID userId, String formId, Object body) {
	}

	public record CreatedForm(UUID formId, String status) {
	}

	public record DefinitionView(Map<String, Object> definition, String status, String shareUrl,
			Integer definitionVersion) {

		// SPEC 5절 116행: 폼 정의 전체에 상태 값을 더한 한 객체로 나간다.
		public Map<String, Object> toBody() {
			Map<String, Object> body = new LinkedHashMap<>(definition);
			body.put("status", status);
			body.put("share_url", shareUrl);
			body.put("definition_version", definitionVersion);
			return body;
		}

	}

	public record StateView(String status, String shareUrl, Integer definitionVersion) {
	}

	public record CreatedFormView(UUID formId, String title, String status, Instant deadline, String shareUrl,
			long responseCount) {
	}

}
