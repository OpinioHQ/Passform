package com.opinio.passform.domain.export;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;

import tools.jackson.databind.json.JsonMapper;

// 내보내기 파일을 만든다(SPEC 241~243행). 키와 열은 SPEC에 적힌 것뿐이다.
// 판정 불가 범위는 파일에 넣지 않는다. 화면이 분석 응답의 coverage로 안내한다(docs/prompts/delegation_export.md).
public final class ExportWriter {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String[] FIXED_COLUMNS = { "response_id", "form_version", "submitted_at", "updated_at" };

	private static final String BOM = "\uFEFF";

	private static final String CRLF = "\r\n";

	private ExportWriter() {
	}

	// definitions는 버전 오름차순이고 responses에 담긴 버전만 있다. responses는 내보낼 행 순서다.
	public static byte[] write(ExportFormat format, Map<Integer, Map<String, Object>> definitions, List<Response> responses) {
		return switch (format) {
			case JSON -> JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(document(definitions, responses));
			case YAML -> yaml(document(definitions, responses)).getBytes(StandardCharsets.UTF_8);
			case CSV -> csv(definitions, responses).getBytes(StandardCharsets.UTF_8);
		};
	}

	private static Map<String, Object> document(Map<Integer, Map<String, Object>> definitions, List<Response> responses) {
		List<Map<String, Object>> versions = new ArrayList<>();
		definitions.forEach((version, definition) -> {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("form_version", version);
			entry.put("form", definition);
			versions.add(entry);
		});
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Response response : responses) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("response_id", response.getId().toString());
			row.put("form_version", response.getFormVersion());
			row.put("submitted_at", String.valueOf(response.getSubmittedAt()));
			row.put("updated_at", response.getUpdatedAt() == null ? null : response.getUpdatedAt().toString());
			row.put("answers", answers(response));
			rows.add(row);
		}
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("form_versions", versions);
		document.put("responses", rows);
		return document;
	}

	// 답한 문항만 넣고 값은 원문 그대로다. 수식 방지는 CSV에만 한다(AC50).
	private static List<Map<String, Object>> answers(Response response) {
		List<Map<String, Object>> answers = new ArrayList<>();
		for (Answer answer : response.getContent()) {
			if (!answer.values().isEmpty()) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("question_id", answer.questionId());
				entry.put("value", answer.value());
				answers.add(entry);
			}
		}
		return answers;
	}

	// 문자열처럼 보이는 숫자·날짜·true·null은 SnakeYAML이 따옴표로 감싸 문자열로 남긴다.
	// 제어문자가 든 답은 기본값이면 !!binary가 되어 JSON과 달라지므로 이스케이프한다(AC48).
	private static String yaml(Map<String, Object> document) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setAllowUnicode(true);
		options.setSplitLines(false);
		options.setNonPrintableStyle(DumperOptions.NonPrintableStyle.ESCAPE);
		return new Yaml(options).dump(document);
	}

	private static String csv(Map<Integer, Map<String, Object>> definitions, List<Response> responses) {
		List<Column> columns = new ArrayList<>();
		definitions.forEach((version, definition) -> DefinitionQuestion.listOf(definition)
			.forEach(question -> columns.add(new Column(version, question.questionId(), question.questionNote()))));

		StringBuilder out = new StringBuilder(BOM);
		List<String> header = new ArrayList<>(List.of(FIXED_COLUMNS));
		columns.forEach(column -> header.add(column.header()));
		appendRow(out, header);
		for (Response response : responses) {
			Map<String, Answer> answers = new HashMap<>();
			response.getContent().forEach(answer -> answers.putIfAbsent(answer.questionId(), answer));
			List<String> cells = new ArrayList<>();
			cells.add(response.getId().toString());
			cells.add(String.valueOf(response.getFormVersion()));
			cells.add(String.valueOf(response.getSubmittedAt()));
			cells.add(response.getUpdatedAt() == null ? "" : response.getUpdatedAt().toString());
			for (Column column : columns) {
				cells.add(column.version() == response.getFormVersion() ? cellOf(answers.get(column.questionId())) : "");
			}
			appendRow(out, cells);
		}
		return out.toString();
	}

	// 건너뛴 문항과 비운 문항은 빈 칸이고 checkbox는 "; "로 잇는다(SPEC 242행).
	private static String cellOf(Answer answer) {
		if (answer == null) {
			return "";
		}
		return answer.value() instanceof Collection<?> ? String.join("; ", answer.values())
				: answer.values().stream().findFirst().orElse("");
	}

	private static void appendRow(StringBuilder out, List<String> cells) {
		out.append(cells.stream().map(ExportWriter::guarded).map(ExportWriter::quoted).collect(Collectors.joining(",")));
		out.append(CRLF);
	}

	// 스프레드시트가 수식으로 실행하지 않게 한다(AC50, AGENTS 3절 5항).
	private static String guarded(String cell) {
		return !cell.isEmpty() && "=+-@".indexOf(cell.charAt(0)) >= 0 ? "'" + cell : cell;
	}

	private static String quoted(String cell) {
		boolean needsQuotes = cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r");
		return needsQuotes ? '"' + cell.replace("\"", "\"\"") + '"' : cell;
	}

	private record Column(int version, String questionId, String questionNote) {

		// 헤더 칸은 버전 번호로 시작하므로 칸 단위 검사로는 잡히지 않는다. 문구 앞에도 수식 방지를 붙인다.
		String header() {
			return version + "." + questionId + " " + guarded(questionNote == null ? "" : questionNote);
		}

	}

}
