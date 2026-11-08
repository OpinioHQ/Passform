package com.opinio.passform.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 규칙으로 못 푼 조건의 LLM 매칭(analyze_api.md 결정 A9, 프롬프트 match_conditions.md). AC21은 미충족(스파이크 37/40).
// 보내는 것은 조건 문구와 문항 문구·유형·선택지뿐이다. 응답 값은 이 클래스에 들어오지 않는다(AGENTS 3절 1항, form_question_info.md Q1).
// 출력은 조건·버전마다 스키마 enum의 허용 값 하나이고, 서버가 다시 확인해 허용 밖 값과 NONE은 버린다.
@Component
public class LlmConditionMatcher {

	private static final String MODEL = "gpt-6-luna";

	private static final String PROMPT_PATH = "response_analysis/prompts/match_conditions.md";

	// 팀 결정(2026-11-02, label_rules.md 4절 (가)): 최신 버전 번호 기준. 문구는 docs/spikes/condition_matching_prompt.md의 latest.
	static final String NUMBERING_RULE = "'N번 문항'은 최신 버전에서 N번인 문항(latest_number가 N인 문항)이다. "
			+ "다른 버전에서는 같은 question_id의 문항으로 판단하고, 그 버전에 그 question_id가 없으면 NONE이다.";

	private static final String NONE = "NONE";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final LlmClient client;

	private final String prompt;

	public LlmConditionMatcher(LlmClient client) {
		this.client = client;
		this.prompt = PromptData.promptOf(PROMPT_PATH).replace("{numbering_rule}", NUMBERING_RULE);
	}

	// versions의 가장 큰 버전을 최신으로 본다. 번호가 든 조건은 규칙으로 풀리지 않아 모든 버전과 함께 오므로
	// 그때 최신은 응답이 남은 버전 중 가장 큰 버전이다(form_question_info.md latest_number).
	public List<ConditionMatch> match(List<String> conditions, List<VersionQuestions> versions) {
		if (conditions.isEmpty() || versions.isEmpty()) {
			return List.of();
		}
		Map<String, String> conditionByKey = new LinkedHashMap<>();
		conditions.forEach(condition -> conditionByKey.put("c" + conditionByKey.size(), condition));
		Map<String, List<String>> allowed = new LinkedHashMap<>();
		versions.forEach(version -> allowed.put(versionKey(version), allowedValues(version)));

		String output = client.respond(new LlmRequest("match_conditions", MODEL, prompt, input(conditionByKey, versions),
				"condition_matches", schema(conditionByKey, allowed), 0.0));
		JsonNode root;
		try {
			root = JSON.readTree(output);
		}
		catch (JacksonException e) {
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
		List<ConditionMatch> matches = new ArrayList<>();
		conditionByKey.forEach((key, condition) -> {
			for (VersionQuestions version : versions) {
				String value = root.path(key).path(versionKey(version)).asString("");
				if (!NONE.equals(value) && allowed.get(versionKey(version)).contains(value)) {
					matches.add(toMatch(condition, version.formVersion(), value));
				}
			}
		});
		return List.copyOf(matches);
	}

	private static String versionKey(VersionQuestions version) {
		return "v" + version.formVersion();
	}

	// label_rules.md 1절의 값: 문항id|option|선택지, 문항id|answered, 문항id|unanswered, NONE.
	private static List<String> allowedValues(VersionQuestions version) {
		List<String> values = new ArrayList<>();
		for (Question question : version.questions()) {
			if (question.isChoice()) {
				question.options().forEach(option -> values.add(question.questionId() + "|option|" + option));
			}
			values.add(question.questionId() + "|answered");
			values.add(question.questionId() + "|unanswered");
		}
		values.add(NONE);
		return List.copyOf(values);
	}

	private static ConditionMatch toMatch(String condition, int formVersion, String value) {
		String[] parts = value.split("\\|", 3);
		MatchKind kind = MatchKind.valueOf(parts[1].toUpperCase(Locale.ROOT));
		return new ConditionMatch(condition, formVersion, parts[0], kind, kind == MatchKind.OPTION ? parts[2] : null);
	}

	// form_question_info.md 매처 입력: is_personal을 뺀 문항 정보.
	private static String input(Map<String, String> conditionByKey, List<VersionQuestions> versions) {
		VersionQuestions latest = versions.get(versions.size() - 1);
		for (VersionQuestions version : versions) {
			if (version.formVersion() > latest.formVersion()) {
				latest = version;
			}
		}
		Map<String, Integer> latestNumbers = new LinkedHashMap<>();
		for (Question question : latest.questions()) {
			latestNumbers.put(question.questionId(), latestNumbers.size() + 1);
		}
		List<Map<String, Object>> formVersions = new ArrayList<>();
		for (VersionQuestions version : versions) {
			List<Map<String, Object>> questions = new ArrayList<>();
			for (Question question : version.questions()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("number", questions.size() + 1);
				item.put("latest_number", latestNumbers.get(question.questionId()));
				item.put("question_id", question.questionId());
				item.put("question_note", question.questionNote());
				item.put("question_type", question.questionType());
				item.put("options", question.options());
				questions.add(item);
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("form_version", version.formVersion());
			entry.put("questions", questions);
			formVersions.add(entry);
		}
		return PromptData.block("conditions", conditionByKey) + "\n" + PromptData.block("form", Map.of("versions", formVersions));
	}

	// 조건·버전마다 허용 값 enum 하나. strict 구조화 출력이라 모든 키가 필수다.
	private static Map<String, Object> schema(Map<String, String> conditionByKey, Map<String, List<String>> allowed) {
		Map<String, Object> versionProperties = new LinkedHashMap<>();
		allowed.forEach((key, values) -> versionProperties.put(key, Map.of("type", "string", "enum", values)));
		Map<String, Object> perCondition = Map.of("type", "object", "properties", versionProperties, "required",
				List.copyOf(allowed.keySet()), "additionalProperties", false);
		Map<String, Object> properties = new LinkedHashMap<>();
		conditionByKey.keySet().forEach(key -> properties.put(key, perCondition));
		return Map.of("type", "object", "properties", properties, "required", List.copyOf(conditionByKey.keySet()),
				"additionalProperties", false);
	}

}
