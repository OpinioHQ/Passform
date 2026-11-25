package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 버전을 나누는 기준이 되는 정의. 운영 설정과 AI 요약 설정은 바뀌어도 새 버전을 만들지 않는다(SPEC 5절 155~157행, AC58).
public final class VersionTarget {

	private static final List<String> NON_VERSIONED_SETTINGS = List.of("editable", "cancellable", "response_limit",
			"ai_summary");

	private static final String ALLOW_RESPONSE_FILES = "allow_response_files";

	private VersionTarget() {
	}

	public static boolean same(Map<String, Object> left, Map<String, Object> right) {
		return of(left).equals(of(right));
	}

	private static Map<String, Object> of(Map<String, Object> definition) {
		Map<String, Object> target = new LinkedHashMap<>(definition);
		target.remove("deadline");
		Map<String, Object> settings = new LinkedHashMap<>(FormJson.object(definition.get("settings")));
		NON_VERSIONED_SETTINGS.forEach(settings::remove);
		target.put("settings", settings);
		target.put("sections", sectionsWithoutDefaultFileFlag(definition.get("sections")));
		return target;
	}

	// allow_response_files는 없으면 false다. 키 없음과 false를 같은 정의로 보아 값이 같은데 새 버전이 생기지 않게 한다.
	private static List<Object> sectionsWithoutDefaultFileFlag(Object sections) {
		List<Object> result = new ArrayList<>();
		for (Object section : FormJson.array(sections)) {
			Map<String, Object> copy = new LinkedHashMap<>(FormJson.object(section));
			List<Object> questions = new ArrayList<>();
			for (Object question : FormJson.array(copy.get("questions"))) {
				Map<String, Object> normalized = new LinkedHashMap<>(FormJson.object(question));
				if (!Boolean.TRUE.equals(normalized.get(ALLOW_RESPONSE_FILES))) {
					normalized.remove(ALLOW_RESPONSE_FILES);
				}
				questions.add(normalized);
			}
			copy.put("questions", questions);
			result.add(copy);
		}
		return result;
	}

}
