package com.opinio.passform.demo.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

// 마커 안만 모델로 보낸다. 마커 밖은 팀이 읽는 문서다(parse_query.md).
final class PromptLoader {

	private static final String START = "<!-- prompt:start -->";

	private static final String END = "<!-- prompt:end -->";

	private PromptLoader() {
	}

	static String load(String path) {
		try (InputStream input = PromptLoader.class.getClassLoader().getResourceAsStream(path)) {
			if (input == null) {
				throw new IllegalStateException("프롬프트가 클래스패스에 없습니다: " + path);
			}
			String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			int start = text.indexOf(START);
			int end = text.indexOf(END);
			if (start < 0 || end < start) {
				throw new IllegalStateException("프롬프트 마커가 없습니다: " + path);
			}
			return text.substring(start + START.length(), end).strip();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

}
