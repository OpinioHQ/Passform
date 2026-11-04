import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opinio.passform.domain.analysis.ConditionEvaluator;
import com.opinio.passform.domain.analysis.ConditionMatch;
import com.opinio.passform.domain.analysis.MatchKind;
import com.opinio.passform.domain.analysis.MatchMode;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.ResponseStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// JSON으로 저장한 답을 읽어 조건 평가까지 걸리는 시간. 실제 ConditionEvaluator를 쓴다. 분기 경로 계산과 LLM은 제외.
public class Measure {

	public static void main(String[] args) {
		JsonMapper mapper = JsonMapper.builder().build();
		String[] departments = { "소프트웨어학과", "컴퓨터공학과", "경영학과", "디자인학과" };
		String[] grades = { "1학년", "2학년", "3학년", "4학년" };
		String longAnswer = "팀 프로젝트로 실제 서비스를 끝까지 만들어 배포해 보고 싶어서 지원했습니다. ".repeat(5);
		for (int size : new int[] { 300, 1000, 5000 }) {
			List<String> rows = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				StringBuilder json = new StringBuilder("[");
				json.append("{\"question_id\":\"q1\",\"value\":\"").append(departments[i % 4]).append("\"},");
				json.append("{\"question_id\":\"q2\",\"value\":\"").append(grades[(i / 4) % 4]).append("\"},");
				for (int q = 3; q <= 9; q++) {
					json.append("{\"question_id\":\"q").append(q).append("\",\"value\":\"답변 ").append(i).append("\"},");
				}
				json.append("{\"question_id\":\"q10\",\"value\":\"").append(longAnswer).append("\"}]");
				rows.add(json.toString());
			}
			List<ConditionMatch> matching = List.of(new ConditionMatch("소프트웨어학과", 1, "q1", MatchKind.OPTION, "소프트웨어학과"),
					new ConditionMatch("4학년", 1, "q2", MatchKind.OPTION, "4학년"));
			double[] times = new double[9];
			int count = 0;
			for (int round = 0; round < times.length; round++) {
				long started = System.nanoTime();
				List<ResponseRecord> records = new ArrayList<>(size);
				for (int i = 0; i < size; i++) {
					Map<String, List<String>> answers = new LinkedHashMap<>();
					for (JsonNode answer : mapper.readTree(rows.get(i))) {
						answers.put(answer.get("question_id").asString(), List.of(answer.get("value").asString()));
					}
					records.add(new ResponseRecord("r" + i, 1, ResponseStatus.SUBMITTED, answers));
				}
				count = new ConditionEvaluator().evaluate(List.of("소프트웨어학과", "4학년"), MatchMode.ALL, matching, records).count();
				times[round] = (System.nanoTime() - started) / 1_000_000.0;
			}
			// 첫 실행은 JVM 예열 때문에 느리다. 예열 후 중앙값과 첫 실행을 함께 본다.
			double first = times[0];
			double[] warmed = Arrays.copyOfRange(times, 3, times.length);
			Arrays.sort(warmed);
			System.out.printf("응답 %,d건: 예열 후 중앙값 %.1fms, 첫 실행 %.1fms, 행 크기 약 %,d바이트, 일치 %d건%n", size, warmed[warmed.length / 2], first,
					rows.get(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length, count);
		}
	}

}
