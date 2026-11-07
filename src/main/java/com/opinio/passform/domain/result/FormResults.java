package com.opinio.passform.domain.result;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GET /forms/{form_id}/results의 출력(SPEC 5절 234행). 이름은 snake_case로 나간다.
public record FormResults(int responseCount, Combined combined, List<Version> versions) {

	public FormResults {
		versions = List.copyOf(versions);
	}

	public record Combined(List<CombinedQuestion> questions) {

		public Combined {
			questions = List.copyOf(questions);
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

	public record Version(int formVersion, int responseCount, List<VersionQuestion> questions) {

		public Version {
			questions = List.copyOf(questions);
		}

	}

	// counts는 정의의 선택지 순서를 지킨다.
	public record VersionQuestion(String questionId, String questionNote, Map<String, Integer> counts) {

		public VersionQuestion {
			counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
		}

	}

}
