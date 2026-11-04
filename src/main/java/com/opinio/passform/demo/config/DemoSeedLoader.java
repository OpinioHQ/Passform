package com.opinio.passform.demo.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.demo.domain.form.ConsentTemplate;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormStatus;
import com.opinio.passform.demo.domain.form.FormVersion;
import com.opinio.passform.demo.domain.response.Response;
import com.opinio.passform.demo.domain.response.SavedForm;
import com.opinio.passform.demo.domain.user.User;
import com.opinio.passform.demo.repository.ConsentTemplateRepository;
import com.opinio.passform.demo.repository.FormRepository;
import com.opinio.passform.demo.repository.FormVersionRepository;
import com.opinio.passform.demo.repository.ResponseRepository;
import com.opinio.passform.demo.repository.SavedFormRepository;
import com.opinio.passform.demo.repository.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// 빈 DB에 가상 시드를 넣는다. data/seed(골든 fixture)는 읽기만 하고, 데모 전용 폼은 data/demo에 둔다.
@Component
@Profile("demo")
public class DemoSeedLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoSeedLoader.class);

	private static final List<Path> SEED_DIRECTORIES = List.of(Path.of("data/seed/forms"), Path.of("data/demo/forms"));

	private static final String DEFAULT_CONSENT = "consent_default";

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final UserRepository users;

	private final ConsentTemplateRepository consentTemplates;

	private final ObjectMapper mapper = JsonMapper.builder().build();

	public DemoSeedLoader(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			SavedFormRepository savedForms, UserRepository users, ConsentTemplateRepository consentTemplates) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.users = users;
		this.consentTemplates = consentTemplates;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		if (forms.count() > 0) {
			log.info("demo_seed skipped existing_forms={}", forms.count());
			return;
		}
		users.save(new User(DemoIdentityFilter.DEFAULT_USER, "데모 사용자", "demo-user@example.com"));
		users.save(new User(DemoIdentityFilter.OTHER_USER, "다른 사용자", "other-user@example.com"));
		consentTemplates.save(new ConsentTemplate(DEFAULT_CONSENT, "신청 접수와 결과 확인", "폼 마감 후 6개월"));
		for (Path directory : SEED_DIRECTORIES) {
			try (Stream<Path> files = Files.list(directory)) {
				for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
					load(file);
				}
			}
		}
		log.info("demo_seed loaded forms={} responses={}", forms.count(), responses.count());
	}

	private void load(Path file) throws IOException {
		JsonNode root = mapper.readTree(Files.readString(file));
		String formId = root.path("form_id").asString();
		int latest = 0;
		JsonNode latestForm = null;
		for (JsonNode version : root.path("form_versions")) {
			int number = version.path("form_version").asInt();
			JsonNode definition = version.path("form");
			versions.save(new FormVersion(formId, number, mapper.writeValueAsString(definition)));
			if (number > latest) {
				latest = number;
				latestForm = definition;
			}
		}
		if (latestForm == null) {
			throw new IllegalStateException("폼 버전이 없습니다: " + file);
		}
		JsonNode settings = latestForm.path("settings");
		forms.save(new Form(formId, root.path("owner_id").asString(DemoIdentityFilter.DEFAULT_USER),
				root.path("slug").asString(formId.replace('_', '-')), FormStatus.OPEN,
				latestForm.path("title").asString(), latest, instant(latestForm.get("deadline")),
				settings.hasNonNull("response_limit") ? settings.get("response_limit").asInt() : null,
				settings.path("editable").asBoolean(false), settings.path("cancellable").asBoolean(false),
				settings.path("ai_summary").path("enabled").asBoolean(false),
				latestForm.path("consent_template_id").asString(DEFAULT_CONSENT),
				settings.path("display").path("default_mode").asString("all_questions"),
				instant(root.get("created_at"), Instant.parse("2026-10-01T00:00:00Z"))));
		for (JsonNode response : root.path("responses")) {
			responses.save(new Response(response.path("response_id").asString(), formId,
					response.hasNonNull("user_id") ? response.get("user_id").asString() : null,
					response.path("form_version").asInt(),
					ResponseStatus.valueOf(response.path("status").asString().toUpperCase()),
					mapper.writeValueAsString(response.path("answers")),
					response.path("ai_summary_agreed").asBoolean(false), instant(response.get("submitted_at")),
					instant(response.get("updated_at"))));
		}
		for (JsonNode saved : root.path("saved_by")) {
			savedForms.save(new SavedForm(saved.path("user_id").asString(), formId, saved.path("state").asString()));
		}
	}

	private static Instant instant(JsonNode value) {
		return instant(value, null);
	}

	private static Instant instant(JsonNode value, Instant fallback) {
		return (value != null && !value.isNull()) ? OffsetDateTime.parse(value.asString()).toInstant() : fallback;
	}

}
