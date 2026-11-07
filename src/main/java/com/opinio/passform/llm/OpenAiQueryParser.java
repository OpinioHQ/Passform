package com.opinio.passform.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 파서의 OpenAI 호출(AGENTS 7절). 제작자의 질의만 보낸다. 키는 OS 환경변수로만 읽고 없어도 앱은 뜬다.
// 입출력 원문과 키는 로그에 남기지 않는다.
@Component
public class OpenAiQueryParser implements QueryParser {

	private static final Logger log = LoggerFactory.getLogger(OpenAiQueryParser.class);

	private static final String MODEL = "gpt-6-luna";

	private static final String PROMPT_PATH = "response_analysis/prompts/parse_query.md";

	private static final String SCHEMA_PATH = "response_analysis/schemas/request_context.schema.json";

	private static final Pattern PROMPT_BLOCK = Pattern
		.compile("(?ms)^<!-- prompt:start -->\\s*$(.*?)^<!-- prompt:end -->\\s*$");

	private final RestClient rest;

	private final String apiKey;

	private final String prompt;

	private final Map<String, Object> schema;

	public OpenAiQueryParser() {
		this(System.getenv("OPENAI_API_KEY"), "https://api.openai.com/v1");
	}

	OpenAiQueryParser(String apiKey, String baseUrl) {
		this.apiKey = apiKey;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requests.setReadTimeout(Duration.ofSeconds(60));
		this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
		this.prompt = promptBetweenMarkers(readResource(PROMPT_PATH));
		this.schema = schemaForRequest(readResource(SCHEMA_PATH));
	}

	@Override
	public String parse(String query, String previousError) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
		long started = System.nanoTime();
		try {
			JsonNode response = rest.post()
				.uri("/responses")
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody(query, previousError))
				.retrieve()
				.body(JsonNode.class);
			if (response == null) {
				throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
			}
			log.info("llm_call purpose=parse_query model={} elapsed_ms={} input_tokens={} output_tokens={}", MODEL,
					(System.nanoTime() - started) / 1_000_000, response.path("usage").path("input_tokens").asInt(-1),
					response.path("usage").path("output_tokens").asInt(-1));
			return outputText(response);
		}
		catch (RestClientException e) {
			log.warn("llm_call_failed purpose=parse_query model={} elapsed_ms={} error={}", MODEL,
					(System.nanoTime() - started) / 1_000_000, e.getClass().getSimpleName());
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
	}

	private Map<String, Object> requestBody(String query, String previousError) {
		String input = previousError == null ? query
				: query + "\n\n이전 출력이 스키마에 맞지 않았다. 오류: " + previousError + "\n스키마에 맞는 JSON 객체 하나만 다시 출력한다.";
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", MODEL);
		body.put("instructions", prompt);
		body.put("input", input);
		body.put("reasoning", Map.of("effort", "none"));
		body.put("temperature", 0);
		// 스키마의 선택 키(match, sort 등)는 strict 모드와 맞지 않는다. 서버가 응답을 다시 검증한다.
		body.put("text", Map.of("format",
				Map.of("type", "json_schema", "name", "request_context", "schema", schema, "strict", false)));
		body.put("store", false);
		return body;
	}

	// 잘린 출력과 거절은 빈 문자열이나 깨진 JSON으로 돌아가 호출하는 쪽의 형식 오류 처리를 탄다.
	private static String outputText(JsonNode response) {
		StringBuilder text = new StringBuilder();
		for (JsonNode item : response.path("output")) {
			if ("message".equals(item.path("type").asString(""))) {
				for (JsonNode content : item.path("content")) {
					if ("output_text".equals(content.path("type").asString(""))) {
						text.append(content.path("text").asString(""));
					}
				}
			}
		}
		return text.toString();
	}

	// 표의 설명문에도 마커 글자가 나오므로 마커만 있는 줄을 찾는다.
	private static String promptBetweenMarkers(String file) {
		Matcher matcher = PROMPT_BLOCK.matcher(file);
		if (!matcher.find()) {
			throw new IllegalStateException("프롬프트 마커가 없다: " + PROMPT_PATH);
		}
		return matcher.group(1).strip();
	}

	// OpenAI 구조화 출력은 $schema 키를 쓰지 않는다. 서버 재검증은 원본 스키마로 한다.
	@SuppressWarnings("unchecked")
	private static Map<String, Object> schemaForRequest(String file) {
		Map<String, Object> loaded = new HashMap<>(JsonMapper.builder().build().readValue(file, Map.class));
		loaded.remove("$schema");
		return loaded;
	}

	private static String readResource(String path) {
		try (InputStream input = new ClassPathResource(path).getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(path + "를 읽지 못했다", e);
		}
	}

}
