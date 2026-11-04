package com.opinio.passform.demo.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.opinio.passform.demo.config.AnalysisProperties;

import tools.jackson.databind.JsonNode;

@Component
public class OpenAiClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

	private final RestClient rest;

	private final String apiKey;

	private final String embeddingModel;

	public OpenAiClient(@Value("${openai.api-key:}") String apiKey, AnalysisProperties properties) {
		this.apiKey = apiKey;
		this.embeddingModel = properties.embeddingModel();
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(60));
		this.rest = RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(factory).build();
	}

	@Override
	public List<String> complete(Request request) {
		Map<String, Object> format = Map.of("type", "json_schema", "name", request.schemaName(), "schema",
				request.schema(), "strict", request.strict());
		Map<String, Object> body = new HashMap<>();
		body.put("model", request.model());
		body.put("instructions", request.instructions());
		body.put("input", request.input());
		body.put("reasoning", Map.of("effort", "none"));
		body.put("text", Map.of("format", format));
		body.put("store", false);
		if (request.zeroTemperature()) {
			body.put("temperature", 0);
		}
		JsonNode response = post("/responses", request.purpose(), request.model(), body);
		List<String> texts = new ArrayList<>();
		for (JsonNode item : response.path("output")) {
			if ("message".equals(item.path("type").asString(""))) {
				for (JsonNode content : item.path("content")) {
					if ("output_text".equals(content.path("type").asString(""))) {
						texts.add(content.path("text").asString(""));
					}
				}
			}
		}
		return texts;
	}

	@Override
	public List<float[]> embed(String purpose, List<String> inputs) {
		JsonNode response = post("/embeddings", purpose, embeddingModel, Map.of("model", embeddingModel, "input", inputs));
		List<float[]> vectors = new ArrayList<>();
		for (JsonNode item : response.path("data")) {
			JsonNode embedding = item.path("embedding");
			float[] vector = new float[embedding.size()];
			for (int index = 0; index < vector.length; index++) {
				vector[index] = embedding.get(index).floatValue();
			}
			vectors.add(vector);
		}
		if (vectors.size() != inputs.size()) {
			throw new LlmUnavailableException("임베딩 응답 수가 입력 수와 다릅니다.");
		}
		return vectors;
	}

	// 관측: 입력·출력 원문은 남기지 않고 용도, 모델, 소요 시간, 토큰만 남긴다.
	private JsonNode post(String path, String purpose, String model, Map<String, Object> body) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new LlmUnavailableException("OPENAI_API_KEY가 설정되지 않았습니다.");
		}
		long started = System.nanoTime();
		try {
			JsonNode response = rest.post()
				.uri(path)
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
			log.info("llm_call purpose={} model={} elapsed_ms={} input_tokens={} output_tokens={}", purpose, model,
					(System.nanoTime() - started) / 1_000_000, response.path("usage").path("input_tokens").asInt(-1),
					response.path("usage").path("output_tokens").asInt(-1));
			return response;
		}
		catch (RestClientException exception) {
			log.warn("llm_call_failed purpose={} model={} elapsed_ms={} error={}", purpose, model,
					(System.nanoTime() - started) / 1_000_000, exception.getClass().getSimpleName());
			throw new LlmUnavailableException("OpenAI 호출 실패: " + exception.getClass().getSimpleName(), exception);
		}
	}

}
