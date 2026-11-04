package com.opinio.passform.demo.domain.form;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.opinio.passform.demo.domain.InvalidRequestException;

// SPEC 5절 160~164행, 216행: 서버가 계산한 경로의 답만 남기고 필수·선택지·형식·글자 수를 다시 검사한다.
public final class AnswerValidator {

	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+");

	// 010은 11자리, 011·016~019는 10~11자리. SPEC 162행(10~11자리)보다 엄격하다(팀 결정 2026-11-04, SPEC 수정 필요).
	private static final Pattern MOBILE_PHONE = Pattern.compile("010\\d{8}|01[16789]\\d{7,8}");

	private AnswerValidator() {
	}

	public static Map<String, List<String>> validate(FormDefinition definition, Map<String, List<String>> submitted) {
		List<Question> path = ResponsePathCalculator.path(definition, submitted);
		Map<String, List<String>> kept = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		List<String> invalidOption = new ArrayList<>();
		List<String> invalidFormat = new ArrayList<>();
		List<String> tooLong = new ArrayList<>();
		for (Question question : path) {
			List<String> values = cleaned(submitted.getOrDefault(question.questionId(), List.of()));
			if (values.isEmpty()) {
				if (question.required()) {
					missing.add(question.questionId());
				}
				continue;
			}
			if (question.isChoice() && !question.options().containsAll(values)
					|| question.isSingleChoice() && values.size() > 1) {
				invalidOption.add(question.questionId());
				continue;
			}
			if (question.isText()) {
				String normalized = normalize(question, values.get(0));
				if (normalized == null) {
					invalidFormat.add(question.questionId());
					continue;
				}
				if (question.characterLimit() != null
						&& normalized.codePointCount(0, normalized.length()) > question.characterLimit()) {
					tooLong.add(question.questionId());
					continue;
				}
				values = List.of(normalized);
			}
			kept.put(question.questionId(), values);
		}
		rejectIfAny("required_missing", missing);
		rejectIfAny("invalid_option", invalidOption);
		rejectIfAny("invalid_input_format_value", invalidFormat);
		rejectIfAny("character_limit_exceeded", tooLong);
		return kept;
	}

	private static List<String> cleaned(List<String> values) {
		return values.stream().filter(value -> value != null && !value.isBlank()).toList();
	}

	private static void rejectIfAny(String error, List<String> questionIds) {
		if (!questionIds.isEmpty()) {
			throw new InvalidRequestException(error, questionIds);
		}
	}

	private static String normalize(Question question, String value) {
		if (question.inputFormat() == null) {
			return value;
		}
		String trimmed = value.trim();
		return switch (question.inputFormat().kind()) {
			case "mobile_phone" -> mobilePhone(trimmed);
			case "landline_phone" -> digits(trimmed, 9, 11);
			case "email" -> EMAIL.matcher(trimmed).matches() ? trimmed : null;
			case "date" -> isRealDate(trimmed) ? trimmed : null;
			default -> trimmed;
		};
	}

	private static String mobilePhone(String value) {
		String stripped = value.replaceAll("[\\s-]", "");
		return MOBILE_PHONE.matcher(stripped).matches() ? stripped : null;
	}

	private static String digits(String value, int min, int max) {
		String stripped = value.replaceAll("[\\s-]", "");
		return stripped.matches("\\d{" + min + "," + max + "}") ? stripped : null;
	}

	private static boolean isRealDate(String value) {
		if (!DATE.matcher(value).matches()) {
			return false;
		}
		try {
			LocalDate.parse(value);
			return true;
		}
		catch (DateTimeParseException exception) {
			return false;
		}
	}

}
