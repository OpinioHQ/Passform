package com.opinio.passform.domain.result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;

// GET /forms/{form_id}/responses/{response_id}의 출력(SPEC 5절 236행, AC62).
// attachments의 항목은 question_id, attachment_id, filename, content_type, size_bytes, available이다(SPEC 5절 222행).
public record ResponseDetail(UUID responseId, int formVersion, Instant submittedAt, Instant updatedAt,
		List<ResponseContentItem> content, List<Map<String, Object>> attachments) {

	public ResponseDetail {
		content = List.copyOf(content);
		attachments = List.copyOf(attachments);
	}

	// value는 단일 값이면 문자열, checkbox면 문자열 배열이다(data/seed와 같은 형태).
	public record ResponseContentItem(String questionId, String questionNote, Object value) {
	}

	// questions는 그 응답의 제출 버전 정의여야 한다(문구도 그 버전 것).
	// 사람이 정한 것(2026-11-07): 답한 문항만, 정의 순서로 넣는다. 정의에 없는 question_id의 답은 뺀다.
	public static ResponseDetail of(Response response, List<DefinitionQuestion> questions) {
		return of(response, questions, List.of());
	}

	public static ResponseDetail of(Response response, List<DefinitionQuestion> questions,
			List<Map<String, Object>> attachments) {
		Map<String, Answer> answers = new HashMap<>();
		for (Answer answer : response.getContent()) {
			if (!answer.values().isEmpty()) {
				answers.putIfAbsent(answer.questionId(), answer);
			}
		}
		List<ResponseContentItem> content = new ArrayList<>();
		for (DefinitionQuestion question : questions) {
			Answer answer = answers.get(question.questionId());
			if (answer != null) {
				Object value = answer.value() instanceof Collection<?> ? answer.values() : answer.values().get(0);
				content.add(new ResponseContentItem(question.questionId(), question.questionNote(), value));
			}
		}
		return new ResponseDetail(response.getId(), response.getFormVersion(), response.getSubmittedAt(),
				response.getUpdatedAt(), content, attachments);
	}

}
