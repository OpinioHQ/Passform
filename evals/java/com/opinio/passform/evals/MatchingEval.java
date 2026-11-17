package com.opinio.passform.evals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;
import com.opinio.passform.llm.ConditionMatcher;

import tools.jackson.databind.JsonNode;

// 조건 매칭 일치율·오매칭률(EVAL_DESIGN 1절). 스파이크 eval2 40건의 조건을 파서 없이 운영 매처에 넣고
// 사람 라벨과 (조건, 버전)별로 비교한다. 판정은 docs/spikes/condition_matching.md 3절을 따른다.
final class MatchingEval {

	static final Path DATA = Path.of("docs/spikes/condition_matching_data");

	static final String NONE = "NONE";

	private MatchingEval() {
	}

	record Request(String requestId, String formId, List<String> conditions) {
	}

	static List<Request> requests() {
		List<Request> requests = new ArrayList<>();
		for (JsonNode line : Json.readLines(DATA.resolve("requests/eval2.jsonl"))) {
			requests.add(new Request(line.path("request_id").asString(), line.path("form_id").asString(),
					Json.strings(line.path("conditions"))));
		}
		return requests;
	}

	// 스파이크 폼은 시드와 형식이 달라(versions 키) 매처 입력인 VersionQuestions로 바꾼다.
	// 매처는 개인정보 여부를 쓰지 않으므로 personal은 false로 둔다.
	static List<VersionQuestions> versions(String formId) {
		JsonNode form = Json.read(DATA.resolve("forms/" + formId + ".json"));
		List<VersionQuestions> versions = new ArrayList<>();
		for (JsonNode version : form.path("versions")) {
			List<Question> questions = new ArrayList<>();
			for (JsonNode question : version.path("questions")) {
				questions.add(new Question(question.path("question_id").asString(), question.path("question_note").asString(),
						question.path("question_type").asString(), Json.strings(question.path("options")), false));
			}
			versions.add(new VersionQuestions(version.path("form_version").asInt(), List.copyOf(questions)));
		}
		return versions;
	}

	// (request_id, cond_idx, form_version) → 라벨
	static Map<String, String> labels() {
		Map<String, String> labels = new LinkedHashMap<>();
		String csv = Json.readString(DATA.resolve("labels/eval2_labels_final.csv")).replace("﻿", "");
		String[] lines = csv.split("\\R");
		for (int index = 1; index < lines.length; index++) {
			if (lines[index].isBlank()) {
				continue;
			}
			String[] cells = lines[index].split(",", -1);
			labels.put(cells[0] + "/" + cells[2] + "/" + cells[4], cells[5].strip());
		}
		return labels;
	}

	static String labelOf(ConditionMatch match) {
		String kind = match.kind().name().toLowerCase(Locale.ROOT);
		return match.questionId() + "|" + kind + ("option".equals(kind) ? "|" + match.option() : "");
	}

	// 요청 한 건을 매칭하고 판정한다. 놓침·오매칭은 스파이크 3절 정의다.
	static Map<String, Object> evaluate(Request request, ConditionMatcher matcher, Map<String, String> labels) {
		List<VersionQuestions> versions = versions(request.formId());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("request_id", request.requestId());
		result.put("form_id", request.formId());
		result.put("conditions", request.conditions());
		List<ConditionMatch> output;
		try {
			output = matcher.match(request.conditions(), versions);
		}
		catch (CallRecorder.BudgetExceeded e) {
			throw e;
		}
		catch (RuntimeException e) {
			result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
			result.put("exact", false);
			result.put("mismatch", false);
			result.put("missed", false);
			return result;
		}
		boolean exact = true;
		boolean mismatch = false;
		boolean missed = false;
		List<Map<String, Object>> rows = new ArrayList<>();
		for (int conditionIndex = 0; conditionIndex < request.conditions().size(); conditionIndex++) {
			String condition = request.conditions().get(conditionIndex);
			for (VersionQuestions version : versions) {
				Set<String> actual = new TreeSet<>();
				for (ConditionMatch match : output) {
					if (match.condition().equals(condition) && match.formVersion() == version.formVersion()) {
						actual.add(labelOf(match));
					}
				}
				if (actual.isEmpty()) {
					actual.add(NONE);
				}
				String label = labels.get(request.requestId() + "/" + conditionIndex + "/" + version.formVersion());
				boolean same = label != null && actual.equals(Set.of(label));
				exact &= same;
				if (!same && !actual.equals(Set.of(NONE))) {
					mismatch = true;
				}
				if (label != null && !NONE.equals(label) && actual.equals(Set.of(NONE))) {
					missed = true;
				}
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("condition", condition);
				row.put("form_version", version.formVersion());
				row.put("label", label);
				row.put("output", actual);
				row.put("same", same);
				rows.add(row);
			}
		}
		result.put("rows", rows);
		result.put("exact", exact);
		result.put("mismatch", mismatch);
		result.put("missed", missed);
		return result;
	}

}
