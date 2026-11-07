package com.opinio.passform.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;

import tools.jackson.databind.json.JsonMapper;

// data/seed/forms의 fixture를 DB에 넣는다. fixture 파일은 읽기만 한다.
// 폼은 마지막 버전으로 배포된 open 상태로 둔다. 응답의 seed ID(rb01 등)는 새 UUID로 바뀌어 responseIds에 담긴다.
public final class SeedForms {

	private static final Path FORMS = Path.of("data", "seed", "forms");

	private SeedForms() {
	}

	public record Seeded(UUID formId, Map<String, UUID> responseIds) {

		public UUID response(String seedId) {
			return responseIds.get(seedId);
		}

	}

	@SuppressWarnings("unchecked")
	public static Seeded load(String file, UUID ownerId, FormRepository forms, FormVersionRepository versions,
			ResponseRepository responses) {
		Map<String, Object> seed = read(file);
		List<Map<String, Object>> formVersions = (List<Map<String, Object>>) seed.get("form_versions");
		int latest = 0;
		Map<String, Object> latestDefinition = Map.of();
		for (Map<String, Object> version : formVersions) {
			int number = ((Number) version.get("form_version")).intValue();
			if (number > latest) {
				latest = number;
				latestDefinition = (Map<String, Object>) version.get("form");
			}
		}
		// 배포 전이(E2)가 아직 없어 필드를 직접 채운다. JDBC로 바꾸면 같은 트랜잭션의 엔티티가 옛 값을 들고 있다.
		Form form = new Form(ownerId, latestDefinition, TestData.NOW);
		ReflectionTestUtils.setField(form, "status", FormStatus.OPEN);
		ReflectionTestUtils.setField(form, "definitionVersion", latest);
		UUID formId = forms.saveAndFlush(form).getId();
		for (Map<String, Object> version : formVersions) {
			versions.saveAndFlush(new FormVersion(formId, ((Number) version.get("form_version")).intValue(),
					(Map<String, Object>) version.get("form"), TestData.NOW));
		}
		Map<String, UUID> responseIds = new LinkedHashMap<>();
		for (Map<String, Object> response : (List<Map<String, Object>>) seed.get("responses")) {
			List<Answer> answers = new ArrayList<>();
			for (Map<String, Object> answer : (List<Map<String, Object>>) response.get("answers")) {
				answers.add(new Answer((String) answer.get("question_id"), answer.get("value")));
			}
			ResponseStatus status = ResponseStatus.valueOf(((String) response.get("status")).toUpperCase(Locale.ROOT));
			String submittedAt = (String) response.get("submitted_at");
			Response saved = responses.saveAndFlush(new Response(formId, null,
					((Number) response.get("form_version")).intValue(), status, answers,
					Boolean.TRUE.equals(response.get("ai_summary_agreed")), null,
					submittedAt == null ? null : Instant.parse(submittedAt)));
			responseIds.put((String) response.get("response_id"), saved.getId());
		}
		return new Seeded(formId, responseIds);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> read(String file) {
		try {
			return JsonMapper.builder().build().readValue(Files.readString(FORMS.resolve(file)), Map.class);
		}
		catch (IOException e) {
			throw new IllegalStateException("fixture를 읽지 못했다: " + file, e);
		}
	}

}
