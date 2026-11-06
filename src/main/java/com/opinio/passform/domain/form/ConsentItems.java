package com.opinio.passform.domain.form;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 공개 조회의 수집 항목(SPEC 5절 210행, AC13). profile_key는 보지 않는다. 문항 순서대로 같은 범주는 한 번만 쓴다.
public final class ConsentItems {

	private static final String UNFORMATTED = "문항별 응답 내용";

	private static final Map<String, String> LABELS = Map.of("name", "이름", "mobile_phone", "휴대전화 번호", "landline_phone",
			"집전화 번호", "address", "주소", "student_id", "학번", "email", "이메일", "date", "날짜");

	private ConsentItems() {
	}

	public static List<String> of(Map<String, Object> definition) {
		Set<String> items = new LinkedHashSet<>();
		for (Object section : FormJson.array(definition.get("sections"))) {
			for (Object value : FormJson.array(FormJson.object(section).get("questions"))) {
				String kind = FormJson.text(FormJson.object(FormJson.object(value).get("input_format")).get("kind"));
				items.add(kind == null ? UNFORMATTED : LABELS.get(kind));
			}
		}
		return List.copyOf(items);
	}

}
