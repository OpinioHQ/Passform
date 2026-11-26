package com.opinio.passform.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

// 사람 라벨. 러너가 만든 라벨 시트(CSV)를 사람이 채우면 JSONL로 옮기고(--import-labels),
// judge 판정과 사람 라벨의 기준별 일치율을 잰다(--judge-agreement, EVAL_DESIGN 3절 "judge 검증").
final class Labels {

	static final Path JUDGE_LABELS = Path.of("evals/judge_labels.jsonl");

	static final List<String> CRITERIA = List.of("c1", "c2", "c3", "c4");

	private Labels() {
	}

	// ---- 시트 → JSONL ----

	// 헤더로 시트 종류를 가린다. 요약 문장 시트는 c1~c4 칸, 주제 라벨 시트는 human_label 칸이 있다.
	// 같은 run_id의 라벨이 이미 있으면 덮어쓰지 않고 멈춘다(다시 옮기려면 그 줄을 지우고 실행한다).
	static void importSheet(Path csv) {
		List<List<String>> rows = parseCsv(Json.readString(csv).replace("﻿", ""));
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("빈 시트: " + csv);
		}
		List<String> header = rows.get(0);
		boolean sentences = header.contains("c1");
		boolean themes = header.contains("human_label");
		if (sentences == themes) {
			throw new IllegalArgumentException("요약 문장 시트(c1~c4)도 주제 라벨 시트(human_label)도 아니다: " + csv);
		}
		Path target = sentences ? JUDGE_LABELS : EvalsMain.THEME_LABELS;
		List<String> lines = new ArrayList<>();
		Set<String> runs = new LinkedHashSet<>();
		List<String> problems = new ArrayList<>();
		Set<String> injectionCases = sentences ? injectionCases() : Set.of();
		for (int index = 1; index < rows.size(); index++) {
			List<String> row = rows.get(index);
			if (row.stream().allMatch(String::isBlank)) {
				continue;
			}
			Map<String, String> cells = new LinkedHashMap<>();
			for (int column = 0; column < header.size(); column++) {
				cells.put(header.get(column), column < row.size() ? unprotect(row.get(column)).strip() : "");
			}
			runs.add(cells.get("run_id"));
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("run_id", cells.get("run_id"));
			if (sentences) {
				line.put("unit_id", cells.get("unit_id"));
				line.put("case_id", cells.get("case_id"));
				for (String criterion : CRITERIA) {
					String value = cells.getOrDefault(criterion, "");
					if (value.isEmpty()) {
						continue;
					}
					Boolean pass = bool(value);
					if (pass == null) {
						problems.add((index + 1) + "행 " + criterion + " 값을 읽지 못했다: " + value);
						continue;
					}
					line.put(criterion, pass);
				}
				// 인젝션 케이스 줄은 c4도 필수다. 비면 c4 일치율 분모에서 조용히 빠진다.
				List<String> required = injectionCases.contains(cells.get("case_id")) ? CRITERIA : List.of("c1", "c2", "c3");
				for (String criterion : required) {
					if (!line.containsKey(criterion)) {
						problems.add((index + 1) + "행 " + cells.get("unit_id") + "의 " + criterion + "가 비어 있다");
					}
				}
				if (!cells.getOrDefault("note", "").isEmpty()) {
					line.put("note", cells.get("note"));
				}
			}
			else {
				if (cells.get("human_label").isEmpty()) {
					problems.add((index + 1) + "행 human_label이 비어 있다");
					continue;
				}
				String label = cells.get("human_label");
				if (!inThemeList(label, cells.getOrDefault("theme_list", ""))) {
					problems.add((index + 1) + "행 human_label이 theme_list에 없다: " + label);
					continue;
				}
				line.put("case_id", cells.get("case_id"));
				line.put("response_id", cells.get("response_id"));
				line.put("question_id", cells.get("question_id"));
				line.put("label", cells.get("human_label"));
			}
			lines.add(Json.line(line));
		}
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("시트를 옮기지 않았다:\n  " + String.join("\n  ", problems));
		}
		if (Files.exists(target)) {
			for (JsonNode existing : Json.readLines(target)) {
				if (runs.contains(existing.path("run_id").asString())) {
					throw new IllegalArgumentException(target + "에 이미 run " + existing.path("run_id").asString()
							+ "의 라벨이 있다. 다시 옮기려면 그 줄을 지우고 실행한다");
				}
			}
		}
		try {
			Files.writeString(target, String.join("\n", lines) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		System.out.println(lines.size() + "줄을 " + target + "에 옮겼다(run " + runs + ")");
	}

	private static Set<String> injectionCases() {
		Set<String> ids = new LinkedHashSet<>();
		for (JsonNode testCase : Json.readLines(EvalsMain.EVALSET)) {
			if (testCase.path("expect").has("injection_resistant")) {
				ids.add(testCase.path("id").asString());
			}
		}
		return ids;
	}

	// 어디에도 맞지 않을 때의 `기타`는 목록에 없어도 된다(README 주제 라벨 절).
	static boolean inThemeList(String label, String themeList) {
		if ("기타".equals(label)) {
			return true;
		}
		for (String theme : themeList.split("\\|")) {
			if (theme.strip().equals(label)) {
				return true;
			}
		}
		return false;
	}

	// 사람이 쓰기 쉬운 값을 받는다.
	static Boolean bool(String value) {
		String normalized = value.strip().toLowerCase();
		if (List.of("true", "1", "o", "y", "yes", "pass", "통과").contains(normalized)) {
			return true;
		}
		if (List.of("false", "0", "x", "n", "no", "fail", "실패").contains(normalized)) {
			return false;
		}
		return null;
	}

	// 러너가 CSV에 쓸 때 수식 방지로 붙인 '를 뗀다.
	private static String unprotect(String cell) {
		return cell.length() > 1 && cell.charAt(0) == '\'' && "=+-@".indexOf(cell.charAt(1)) >= 0 ? cell.substring(1) : cell;
	}

	static List<List<String>> parseCsv(String text) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < text.length(); index++) {
			char c = text.charAt(index);
			if (quoted) {
				if (c == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
					cell.append('"');
					index++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else {
					cell.append(c);
				}
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
					index++;
				}
				row.add(cell.toString());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			}
			else {
				cell.append(c);
			}
		}
		if (cell.length() > 0 || !row.isEmpty()) {
			row.add(cell.toString());
			rows.add(row);
		}
		return rows;
	}

	// ---- judge 일치율 ----

	// run의 judge 판정과 사람 라벨을 판정 단위·기준별로 비교한다. judge만 다시 돌린 run이면 원래 run의 라벨을 쓴다.
	// judge의 "unclear"와 오류는 불일치다. 코드가 먼저 걸러 judge를 부르지 않은 문장은 비교에서 빼고 건수만 남긴다.
	static void judgeAgreement(String runId) {
		Path source = EvalsMain.RESULTS.resolve(runId + ".json");
		if (!Files.exists(source)) {
			throw new IllegalArgumentException("결과 파일이 없다: " + source);
		}
		JsonNode result = Json.read(source);
		String labelRun = result.path("run").path("judge_only_of").isString()
				? result.path("run").path("judge_only_of").asString() : runId;
		Map<String, JsonNode> human = new LinkedHashMap<>();
		if (Files.exists(JUDGE_LABELS)) {
			for (JsonNode line : Json.readLines(JUDGE_LABELS)) {
				if (labelRun.equals(line.path("run_id").asString())) {
					human.put(line.path("unit_id").asString(), line);
				}
			}
		}
		if (human.isEmpty()) {
			throw new IllegalArgumentException(JUDGE_LABELS + "에 run " + labelRun + "의 라벨이 없다");
		}
		Map<String, int[]> counts = new LinkedHashMap<>();
		CRITERIA.forEach(criterion -> counts.put(criterion, new int[2]));
		List<Map<String, Object>> mismatches = new ArrayList<>();
		int prefiltered = 0;
		int unlabeled = 0;
		for (JsonNode testCase : result.path("cases")) {
			for (JsonNode unit : testCase.path("judge_units")) {
				String unitId = unit.path("unit_id").asString();
				JsonNode label = human.get(unitId);
				if (label == null) {
					unlabeled++;
					continue;
				}
				JsonNode judge = unit.path("judge");
				if (judge.has("skipped")) {
					prefiltered++;
					continue;
				}
				for (JsonNode criterionNode : unit.path("criteria")) {
					String criterion = criterionNode.asString();
					if (!label.has(criterion)) {
						continue;
					}
					JsonNode pass = judge.path("criteria").path(criterion).path("pass");
					Object judged = judge.has("error") ? "error" : pass.isBoolean() ? pass.asBoolean() : pass.asString("missing");
					boolean agree = judged instanceof Boolean value && value == label.path(criterion).asBoolean();
					int[] count = counts.get(criterion);
					count[1]++;
					if (agree) {
						count[0]++;
					}
					else {
						Map<String, Object> mismatch = new LinkedHashMap<>();
						mismatch.put("unit_id", unitId);
						mismatch.put("criterion", criterion);
						mismatch.put("judge", judged);
						mismatch.put("human", label.path(criterion).asBoolean());
						mismatches.add(mismatch);
					}
				}
			}
		}
		Map<String, Object> agreement = new LinkedHashMap<>();
		counts.forEach((criterion, count) -> {
			if (count[1] == 0) {
				return;
			}
			double rate = (double) count[0] / count[1];
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("value", Math.round(rate * 10000) / 10000.0);
			entry.put("numerator", count[0]);
			entry.put("denominator", count[1]);
			entry.put("target", "≥80%");
			entry.put("met", rate >= 0.80 - 1e-9);
			agreement.put(criterion, entry);
		});
		Map<String, Object> run = new LinkedHashMap<>();
		run.put("mode", "judge-agreement");
		run.put("of", runId);
		run.put("label_run", labelRun);
		run.put("judge_version", result.path("run").path("judge_version").asString());
		run.put("created_at", Instant.now().toString());
		run.put("units_prefiltered", prefiltered);
		run.put("units_unlabeled", unlabeled);
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("run", run);
		report.put("agreement", agreement);
		report.put("mismatches", mismatches);
		Path out = EvalsMain.RESULTS.resolve("judge_agreement_" + runId + ".json");
		Json.write(out, Json.pretty(report) + "\n");
		System.out.println("judge 일치율 (" + result.path("run").path("judge_version").asString() + ", 라벨 run " + labelRun
				+ ", 코드가 거른 문장 " + prefiltered + "개 제외, 라벨 없는 문장 " + unlabeled + "개)");
		agreement.forEach((criterion, entry) -> {
			Map<String, Object> value = EvalsMain.castMap(entry);
			System.out.println("  " + criterion + ": " + value.get("value") + " (" + value.get("numerator") + "/"
					+ value.get("denominator") + ", 목표 ≥80%, 충족 " + value.get("met") + ")");
		});
		System.out.println("엇갈린 판정 " + mismatches.size() + "건. 결과 파일: " + out);
	}

}
