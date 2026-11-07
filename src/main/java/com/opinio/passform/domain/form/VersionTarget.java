package com.opinio.passform.domain.form;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 버전을 나누는 기준이 되는 정의. 운영 설정과 AI 요약 설정은 바뀌어도 새 버전을 만들지 않는다(SPEC 5절 155~157행, AC58).
public final class VersionTarget {

	private static final List<String> NON_VERSIONED_SETTINGS = List.of("editable", "cancellable", "response_limit",
			"ai_summary");

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
		return target;
	}

}
