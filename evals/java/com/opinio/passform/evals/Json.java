package com.opinio.passform.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

// 러너 공용 JSON 도구. API 출력은 제품과 같은 snake_case로 직렬화한다.
final class Json {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	static final JsonMapper API = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

	private Json() {
	}

	static JsonNode read(Path path) {
		return MAPPER.readTree(readString(path));
	}

	static String readString(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(path + "를 읽지 못했다", e);
		}
	}

	static List<JsonNode> readLines(Path path) {
		List<JsonNode> lines = new ArrayList<>();
		for (String line : readString(path).split("\\R")) {
			if (!line.isBlank()) {
				lines.add(MAPPER.readTree(line));
			}
		}
		return lines;
	}

	static String line(Object value) {
		return MAPPER.writeValueAsString(value);
	}

	static String pretty(Object value) {
		return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
	}

	static JsonNode tree(Object value) {
		return MAPPER.valueToTree(value);
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> map(JsonNode node) {
		return MAPPER.convertValue(node, Map.class);
	}

	static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		if (array != null) {
			array.forEach(item -> values.add(item.asString()));
		}
		return values;
	}

	static void write(Path path, String content) {
		try {
			Files.createDirectories(path.toAbsolutePath().getParent());
			Files.writeString(path, content, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(path + "를 쓰지 못했다", e);
		}
	}

}
