package com.opinio.passform.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.ContactMasker;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.llm.ConditionMatcher;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;

import tools.jackson.databind.JsonNode;

// 케이스의 form을 모의 저장소에 올린다. 골든 테스트(AnalysisGoldenTest, SummaryGoldenTest)와 같은 방식으로
// 시드 ID를 순서를 유지하는 UUID로 바꿔 넣고, 결과를 시드 ID로 되돌린다.
final class Fixture {

	static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	static final List<Path> FORM_DIRS = List.of(Path.of("data/seed/forms"), Path.of("evals/fixtures"));

	final String formName;

	final UUID formId = seedUuid(0);

	final Form form;

	final FormRepository forms;

	final FormVersionRepository formVersions;

	final ResponseRepository responses;

	// 시드 응답 원문(상태와 무관하게 모두). 키는 시드 ID.
	final Map<String, JsonNode> rows = new LinkedHashMap<>();

	final Map<Integer, VersionQuestions> versions = new TreeMap<>();

	final Map<String, String> seedIdByUuid = new HashMap<>();

	private Fixture(String formName, JsonNode fixture) {
		this.formName = formName;
		Map<FormVersionId, FormVersion> stored = new HashMap<>();
		int latest = 0;
		Map<String, Object> latestDefinition = Map.of();
		for (JsonNode entry : fixture.path("form_versions")) {
			int version = entry.path("form_version").asInt();
			Map<String, Object> definition = Json.map(entry.path("form"));
			stored.put(new FormVersionId(formId, version), new FormVersion(formId, version, definition, Instant.EPOCH));
			versions.put(version, VersionQuestions.of(version, definition));
			if (version > latest) {
				latest = version;
				latestDefinition = definition;
			}
		}
		form = new Form(OWNER, latestDefinition, Instant.EPOCH);
		setField(form, "id", formId);
		setField(form, "definitionVersion", latest);

		fixture.path("responses").forEach(row -> rows.put(row.path("response_id").asString(), row));
		List<String> seedIds = rows.keySet().stream().sorted().toList();
		List<Response> submitted = new ArrayList<>();
		for (JsonNode row : rows.values()) {
			if (!"submitted".equals(row.path("status").asString())) {
				continue;
			}
			List<Answer> answers = new ArrayList<>();
			for (JsonNode answer : row.path("answers")) {
				answers.add(new Answer(answer.path("question_id").asString(), Json.MAPPER.convertValue(answer.get("value"),
						Object.class)));
			}
			String seedId = row.path("response_id").asString();
			UUID id = seedUuid(seedIds.indexOf(seedId) + 1);
			seedIdByUuid.put(id.toString(), seedId);
			Response response = new Response(formId, OWNER, row.path("form_version").asInt(), ResponseStatus.SUBMITTED,
					answers, row.path("ai_summary_agreed").asBoolean(false), null,
					Instant.parse(row.path("submitted_at").asString()));
			setField(response, "id", id);
			submitted.add(response);
		}
		submitted.sort(Comparator.comparing(Response::getFormVersion)
			.thenComparing(Response::getSubmittedAt)
			.thenComparing(Response::getId));

		Form owned = form;
		forms = proxy(FormRepository.class, (method, args) -> switch (method) {
			case "findById" -> formId.equals(args[0]) ? Optional.of(owned) : Optional.empty();
			default -> null;
		});
		formVersions = proxy(FormVersionRepository.class, (method, args) -> switch (method) {
			case "findById" -> Optional.ofNullable(stored.get(args[0]));
			default -> null;
		});
		List<Response> submittedList = List.copyOf(submitted);
		responses = proxy(ResponseRepository.class, (method, args) -> switch (method) {
			case "findSubmittedNotDeletedByFormId" -> formId.equals(args[0]) ? submittedList : List.of();
			default -> null;
		});
	}

	static Fixture load(String formName) {
		for (Path dir : FORM_DIRS) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
					JsonNode fixture = Json.read(file);
					if (formName.equals(fixture.path("form_id").asString())) {
						return new Fixture(formName, fixture);
					}
				}
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		throw new IllegalArgumentException("form을 찾지 못했다: " + formName);
	}

	private interface Handler {

		Object handle(String method, Object[] args);

	}

	// 러너가 쓰는 조회만 답한다. 다른 메서드를 부르면 실패한다.
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (self, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "toString" -> "모의 " + type.getSimpleName();
					case "hashCode" -> System.identityHashCode(self);
					case "equals" -> self == args[0];
					default -> null;
				};
			}
			Object result = handler.handle(method.getName(), args);
			if (result == null) {
				throw new UnsupportedOperationException("모의 저장소가 지원하지 않는 호출: " + type.getSimpleName() + "."
						+ method.getName());
			}
			return result;
		});
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(name + " 필드를 채우지 못했다", e);
		}
	}

	static UUID seedUuid(int index) {
		return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
	}

	String seedId(String uuid) {
		return seedIdByUuid.getOrDefault(uuid, uuid);
	}

	List<String> seedIds(JsonNode uuids) {
		List<String> ids = new ArrayList<>();
		if (uuids != null) {
			uuids.forEach(id -> ids.add(seedId(id.asString())));
		}
		return ids;
	}

	JsonNode row(String seedId) {
		return rows.get(seedId);
	}

	Question question(int formVersion, String questionId) {
		VersionQuestions version = versions.get(formVersion);
		if (version == null) {
			return null;
		}
		return version.questions().stream().filter(q -> q.questionId().equals(questionId)).findFirst().orElse(null);
	}

	// 답 값을 제품과 같이 문자열로 합친다. 비었으면 빈 문자열.
	static String text(JsonNode value) {
		if (value == null || value.isNull()) {
			return "";
		}
		if (value.isArray()) {
			List<String> parts = new ArrayList<>();
			value.forEach(part -> parts.add(part.asString()));
			return String.join(" ", parts);
		}
		return value.asString();
	}

	String answer(String seedId, String questionId) {
		JsonNode row = rows.get(seedId);
		if (row == null) {
			return "";
		}
		for (JsonNode answer : row.path("answers")) {
			if (questionId.equals(answer.path("question_id").asString())) {
				return text(answer.get("value"));
			}
		}
		return "";
	}

	String maskedAnswer(String seedId, String questionId) {
		return ContactMasker.mask(answer(seedId, questionId));
	}

	boolean consented(String seedId) {
		JsonNode row = rows.get(seedId);
		return row != null && row.path("ai_summary_agreed").asBoolean(false);
	}

	// 가린 답 원문 → (시드 ID, 문항 ID). 분류 요청의 답을 응답으로 되돌릴 때 쓴다.
	Map<String, String> answerKeyByMaskedText() {
		Map<String, String> keys = new HashMap<>();
		rows.forEach((seedId, row) -> row.path("answers").forEach(answer -> {
			String text = text(answer.get("value"));
			if (!text.isBlank()) {
				keys.putIfAbsent(ContactMasker.mask(text), seedId + "." + answer.path("question_id").asString());
			}
		}));
		return keys;
	}

	// 같은 케이스 안에서 같은 매칭 요청을 다시 하면 처음 결과를 쓴다(count의 일치 응답 확인용, 추가 LLM 호출 없음).
	static ConditionMatcher memoized(ConditionMatcher matcher) {
		Map<String, List<ConditionMatch>> cache = new HashMap<>();
		return (conditions, versions) -> cache.computeIfAbsent(conditions + "|" + versions,
				key -> matcher.match(conditions, versions));
	}

}
