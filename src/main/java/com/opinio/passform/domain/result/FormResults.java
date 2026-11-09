package com.opinio.passform.domain.result;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GET /forms/{form_id}/results의 출력(SPEC 5절 234행). 이름은 snake_case로 나간다.
public record FormResults(int responseCount, Combined combined, List<Version> versions) {

	public FormResults {
		versions = List.copyOf(versions);
	}

	// textQuestions·questionOrder는 사람 결정(2026-11-09)으로 더한 필드다. questions의 이름·의미는 그대로다.
	public record Combined(List<CombinedQuestion> questions, List<CombinedTextQuestion> textQuestions,
			List<QuestionRef> questionOrder) {

		public Combined {
			questions = List.copyOf(questions);
			textQuestions = List.copyOf(textQuestions);
			questionOrder = List.copyOf(questionOrder);
		}

	}

	// question_id·문구·유형이 같은 문항 묶음. formVersions는 이 문항이 정의에 있는 제출 버전이다.
	public record CombinedQuestion(String questionId, String questionNote, String questionType,
			List<Integer> formVersions, List<CombinedOption> options) {

		public CombinedQuestion {
			formVersions = List.copyOf(formVersions);
			options = List.copyOf(options);
		}

	}

	// 같은 선택지 문구끼리 합친 응답 수. formVersions는 이 선택지가 정의에 있는 버전이다(0건이어도 포함).
	public record CombinedOption(String label, int count, List<Integer> formVersions) {

		public CombinedOption {
			formVersions = List.copyOf(formVersions);
		}

	}

	// 단답·장문 문항 묶음(선택형과 같은 묶기 규칙). answers는 최신 제출이 먼저다.
	public record CombinedTextQuestion(String questionId, String questionNote, String questionType,
			List<Integer> formVersions, List<TextAnswer> answers) {

		public CombinedTextQuestion {
			formVersions = List.copyOf(formVersions);
			answers = List.copyOf(answers);
		}

	}

	public record Version(int formVersion, int responseCount, List<VersionQuestion> questions,
			List<VersionTextQuestion> textQuestions, List<QuestionRef> questionOrder) {

		public Version {
			questions = List.copyOf(questions);
			textQuestions = List.copyOf(textQuestions);
			questionOrder = List.copyOf(questionOrder);
		}

	}

	// counts는 정의의 선택지 순서를 지킨다.
	public record VersionQuestion(String questionId, String questionNote, Map<String, Integer> counts) {

		public VersionQuestion {
			counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
		}

	}

	public record VersionTextQuestion(String questionId, String questionNote, String questionType,
			List<TextAnswer> answers) {

		public VersionTextQuestion {
			answers = List.copyOf(answers);
		}

	}

	// 빈 답은 넣지 않는다. submittedAt은 제출 시각을 모르면(통계만 계산할 때) null이다.
	public record TextAnswer(String responseId, int formVersion, String value, Instant submittedAt) {
	}

	// 화면이 선택형과 단답·장문 카드를 섞어 놓을 순서. questions·textQuestions의 항목을 이 세 값으로 찾는다.
	public record QuestionRef(String questionId, String questionNote, String questionType) {
	}

}
