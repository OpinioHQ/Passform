package com.opinio.passform.domain.response;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

// responses.content 배열의 한 항목. value는 문자열이고 checkbox는 문자열 배열이다(data/seed와 같은 형태).
public record Answer(@JsonProperty("question_id") String questionId, Object value) {

	// 단일 값도 1개짜리 목록으로 돌려준다. 값이 비었으면 빈 목록이다.
	public List<String> values() {
		if (value instanceof Collection<?> items) {
			return items.stream().filter(Objects::nonNull).map(String::valueOf).filter(item -> !item.isBlank()).toList();
		}
		if (value == null || String.valueOf(value).isBlank()) {
			return List.of();
		}
		return List.of(String.valueOf(value));
	}

}
