package com.opinio.passform.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

// 프롬프트 파일 읽기와 데이터 영역 만들기. 응답 원문은 이 구분자 안에만 들어간다(AC31).
final class PromptData {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Pattern PROMPT_BLOCK = Pattern
		.compile("(?ms)^<!-- prompt:start -->\\s*$(.*?)^<!-- prompt:end -->\\s*$");

	private PromptData() {
	}

	static String promptOf(String path) {
		try (InputStream input = new ClassPathResource(path).getInputStream()) {
			Matcher matcher = PROMPT_BLOCK.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8));
			if (!matcher.find()) {
				throw new IllegalStateException("프롬프트 마커가 없다: " + path);
			}
			return matcher.group(1).strip();
		}
		catch (IOException e) {
			throw new UncheckedIOException(path + "를 읽지 못했다", e);
		}
	}

	// 값을 JSON으로 적고 꺾쇠를 이스케이프한다. 답에 "</answers>"가 있어도 구분자를 닫지 못한다.
	static String block(String tag, Object value) {
		String json = JSON.writeValueAsString(value).replace("<", "\\u003c").replace(">", "\\u003e");
		return "<" + tag + ">\n" + json + "\n</" + tag + ">";
	}

}
