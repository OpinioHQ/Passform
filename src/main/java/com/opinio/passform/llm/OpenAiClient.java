package com.opinio.passform.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import tools.jackson.databind.JsonNode;

// 요약의 OpenAI 호출(AGENTS 7절). 키는 OS 환경변수로만 읽고 없어도 앱은 뜬다.
// 로그에는 용도, 모델, 소요 시간, 토큰만 남긴다. 키, 응답 원문, 요청 본문은 남기지 않는다.
@Component
public class OpenAiClient implements LlmClient, EmbeddingClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

	private static final String EMBEDDING_MODEL = "text-embedding-3-small";

	private static final int EMBEDDING_BATCH = 256;

	private final RestClient rest;

	private final String apiKey;

	public OpenAiClient() {
		this(System.getenv("OPENAI_API_KEY"), "https://api.openai.com/v1");
	}

	OpenAiClient(String apiKey, String baseUrl) {
		this.apiKey = apiKey;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requests.setReadTimeout(Duration.ofSeconds(60));
		this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
	}

	@Override
	public String respond(LlmRequest request) {
		return outputText(post("/responses", responseBody(request), request.purpose(), request.model()));
	}

	@Override
	public List<double[]> embed(String purpose, List<String> texts) {
		List<double[]> vectors = new ArrayList<>();
		for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH) {
			List<String> batch = texts.subList(start, Math.min(start + EMBEDDING_BATCH, texts.size()));
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("model", EMBEDDING_MODEL);
			body.put("input", batch);
			body.put("encoding_format", "float");
			vectors.addAll(vectorsOf(post("/embeddings", body, purpose, EMBEDDING_MODEL), batch.size()));
		}
		return vectors;
	}

	private JsonNode post(String path, Map<String, Object> body, String purpose, String model) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
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
			if (response == null) {
				throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
			}
			JsonNode usage = response.path("usage");
			log.info("llm_call purpose={} model={} elapsed_ms={} input_tokens={} output_tokens={} total_tokens={}", purpose,
					model, (System.nanoTime() - started) / 1_000_000, usage.path("input_tokens").asInt(-1),
					usage.path("output_tokens").asInt(-1), usage.path("total_tokens").asInt(-1));
			return response;
		}
		catch (RestClientException e) {
			log.warn("llm_call_failed purpose={} model={} elapsed_ms={} error={}", purpose, model,
					(System.nanoTime() - started) / 1_000_000, e.getClass().getSimpleName());
			throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
		}
	}

	private static Map<String, Object> responseBody(LlmRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", request.model());
		body.put("instructions", request.instructions());
		body.put("input", request.input());
		body.put("reasoning", Map.of("effort", "none"));
		if (request.temperature() != null) {
			body.put("temperature", request.temperature());
		}
		body.put("text", Map.of("format",
				Map.of("type", "json_schema", "name", request.schemaName(), "schema", request.schema(), "strict", true)));
		body.put("store", false);
		return body;
	}

	// 잘린 출력과 거절은 빈 문자열로 돌아가 호출하는 쪽의 형식 검사에서 503이 된다.
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

	private static List<double[]> vectorsOf(JsonNode response, int expected) {
		double[][] vectors = new double[expected][];
		for (JsonNode item : response.path("data")) {
			int index = item.path("index").asInt(-1);
			if (index < 0 || index >= expected) {
				throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
			}
			JsonNode embedding = item.path("embedding");
			double[] vector = new double[embedding.size()];
			for (int position = 0; position < vector.length; position++) {
				vector[position] = embedding.get(position).asDouble();
			}
			vectors[index] = vector;
		}
		for (double[] vector : vectors) {
			if (vector == null || vector.length == 0) {
				throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
			}
		}
		return List.of(vectors);
	}

}
