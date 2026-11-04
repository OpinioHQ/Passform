package com.opinio.passform.demo.domain.analysis;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.Question;

// 문항을 가리키는 표현(조건의 앞부분, sort.by, target)을 그 버전의 문항으로 푼다. LLM을 쓰지 않는다.
public final class QuestionResolver {

	private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\s*번(\\s*문항)?$");

	private QuestionResolver() {
	}

	public static List<Question> resolve(String expression, FormDefinition definition, FormDefinition latest) {
		String wanted = normalize(expression);
		Matcher numbered = NUMBERED.matcher(wanted);
		if (numbered.matches()) {
			return byLatestNumber(Integer.parseInt(numbered.group(1)), definition, latest);
		}
		List<Question> exact = definition.questions().stream()
			.filter(question -> normalize(question.questionNote()).equals(wanted)).toList();
		if (!exact.isEmpty() || wanted.length() < 2) {
			return exact;
		}
		return definition.questions().stream().filter(question -> {
			String note = normalize(question.questionNote());
			return note.contains(wanted) || (note.length() >= 2 && wanted.contains(note));
		}).toList();
	}

	public static String normalize(String text) {
		return (text == null) ? "" : text.replaceAll("\\s+", "").toLowerCase();
	}

	// "N번 문항"은 최신 버전의 N번이다. 다른 버전에서는 같은 question_id로 찾는다(스파이크 numbering latest).
	private static List<Question> byLatestNumber(int number, FormDefinition definition, FormDefinition latest) {
		List<Question> latestQuestions = latest.questions();
		if (number < 1 || number > latestQuestions.size()) {
			return List.of();
		}
		String questionId = latestQuestions.get(number - 1).questionId();
		return definition.question(questionId).map(List::of).orElse(List.of());
	}

}
