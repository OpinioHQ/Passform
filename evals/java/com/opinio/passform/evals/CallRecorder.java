package com.opinio.passform.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.opinio.passform.llm.EmbeddingClient;
import com.opinio.passform.llm.LlmClient;
import com.opinio.passform.llm.LlmRequest;
import com.opinio.passform.llm.QueryParser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

// LLM 경계(파서, 매처, 분류, 임베딩, 요약, judge)를 감싸 요청과 응답을 기록하고, stored 모드에서는 재생한다.
// 재생 키는 요청 해시가 아니라 (케이스 id, 용도, 용도 안의 호출 순서)다. 요청 본문은 재생할 때도 코드가 새로 만든 것을 쓴다.
final class CallRecorder {

	enum Mode {
		LIVE, STORED
	}

	static final class BudgetExceeded extends RuntimeException {

		BudgetExceeded(int maxCalls) {
			super("호출 상한 " + maxCalls + "회에 닿아 멈췄다");
		}

	}

	static final class StoredMissing extends RuntimeException {

		StoredMissing(String key) {
			super("저장된 응답이 없다: " + key);
		}

	}

	private final Mode mode;

	private final Path writeDir;

	private final int maxCalls;

	private final Map<String, JsonNode> replay = new HashMap<>();

	private int liveCalls;

	private int replayedCalls;

	private String caseId;

	private final Map<String, Integer> counters = new HashMap<>();

	private final List<Map<String, Object>> caseCalls = new ArrayList<>();

	// live: writeDir에 저장한다. stored: readDir의 응답을 재생한다.
	CallRecorder(Mode mode, Path writeDir, Path readDir, int maxCalls) {
		this.mode = mode;
		this.writeDir = writeDir;
		this.maxCalls = maxCalls;
		if (mode == Mode.STORED) {
			loadReplay(readDir);
		}
	}

	private void loadReplay(Path readDir) {
		if (!Files.isDirectory(readDir)) {
			throw new IllegalArgumentException("저장된 run이 없다: " + readDir);
		}
		try (Stream<Path> files = Files.list(readDir)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".jsonl")).sorted().toList()) {
				for (JsonNode entry : Json.readLines(file)) {
					replay.put(key(entry.path("case_id").asString(), entry.path("category").asString(),
							entry.path("order").asInt()), entry);
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	Mode mode() {
		return mode;
	}

	int liveCalls() {
		return liveCalls;
	}

	int replayedCalls() {
		return replayedCalls;
	}

	void startCase(String id) {
		caseId = id;
		counters.clear();
		caseCalls.clear();
	}

	// 이 케이스에서 오간 호출. 요청 본문은 이번 실행에서 코드가 만든 것이다.
	List<Map<String, Object>> caseCalls() {
		return List.copyOf(caseCalls);
	}

	private static String key(String caseId, String category, int order) {
		return caseId + "/" + category + "/" + order;
	}

	static String category(String purpose) {
		if (purpose.equals("parse_query")) {
			return "parser";
		}
		if (purpose.equals("match_conditions")) {
			return "matcher";
		}
		if (purpose.equals("classify_themes")) {
			return "classify";
		}
		if (purpose.startsWith("summarize")) {
			return "summary";
		}
		if (purpose.equals("judge")) {
			return "judge";
		}
		return "other:" + purpose;
	}

	private int nextOrder(String category) {
		return counters.merge(category, 1, Integer::sum);
	}

	// order가 null이면 그 용도 안의 호출 순서를 쓴다. judge는 판정 단위 번호를 order로 준다.
	JsonNode exchange(String category, Integer order, String purpose, Map<String, Object> request,
			Supplier<JsonNode> live) {
		int position = order != null ? order : nextOrder(category);
		String key = key(caseId, category, position);
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("case_id", caseId);
		entry.put("category", category);
		entry.put("order", position);
		entry.put("purpose", purpose);
		entry.put("request", request);
		caseCalls.add(entry);
		if (mode == Mode.STORED) {
			JsonNode stored = replay.get(key);
			if (stored == null) {
				entry.put("error", "stored_missing");
				throw new StoredMissing(key);
			}
			replayedCalls++;
			entry.put("request_changed", !Json.tree(request).equals(stored.path("request")));
			if (stored.hasNonNull("error")) {
				entry.put("error", stored.get("error").asString());
				throw new IllegalStateException("저장된 호출이 실패였다: " + key);
			}
			JsonNode response = stored.get("response");
			entry.put("response", response);
			return response;
		}
		if (liveCalls >= maxCalls) {
			caseCalls.remove(caseCalls.size() - 1);
			throw new BudgetExceeded(maxCalls);
		}
		liveCalls++;
		try {
			JsonNode response = live.get();
			entry.put("response", response);
			return response;
		}
		catch (RuntimeException e) {
			entry.put("error", e.getClass().getSimpleName());
			throw e;
		}
		finally {
			append(entry);
		}
	}

	private void append(Map<String, Object> entry) {
		Path file = writeDir.resolve(caseId.replaceAll("[^A-Za-z0-9_.-]", "_") + ".jsonl");
		try {
			Files.createDirectories(writeDir);
			Files.writeString(file, Json.line(entry) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	QueryParser parser(QueryParser real) {
		return (query, previousError) -> {
			Map<String, Object> request = new LinkedHashMap<>();
			request.put("purpose", "parse_query");
			request.put("model", "gpt-6-luna");
			request.put("query", query);
			request.put("previous_error", previousError);
			return exchange("parser", null, "parse_query", request,
					() -> Json.tree(real.parse(query, previousError))).asString();
		};
	}

	LlmClient llm(LlmClient real) {
		return llmRequest -> respond(llmRequest, null, real);
	}

	String respond(LlmRequest llmRequest, Integer order, LlmClient real) {
		return exchange(category(llmRequest.purpose()), order, llmRequest.purpose(), requestOf(llmRequest),
				() -> Json.tree(real.respond(llmRequest)))
			.asString();
	}

	static Map<String, Object> requestOf(LlmRequest llmRequest) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("purpose", llmRequest.purpose());
		request.put("model", llmRequest.model());
		request.put("instructions", llmRequest.instructions());
		request.put("input", llmRequest.input());
		request.put("schema_name", llmRequest.schemaName());
		request.put("schema", llmRequest.schema());
		request.put("temperature", llmRequest.temperature());
		return request;
	}

	EmbeddingClient embeddings(EmbeddingClient real) {
		return (purpose, texts) -> {
			Map<String, Object> request = new LinkedHashMap<>();
			request.put("purpose", purpose);
			request.put("model", "text-embedding-3-small");
			request.put("input", texts);
			JsonNode response = exchange("embedding", null, purpose, request, () -> {
				ArrayNode vectors = Json.MAPPER.createArrayNode();
				for (double[] vector : real.embed(purpose, texts)) {
					ArrayNode values = vectors.addArray();
					for (double value : vector) {
						values.add(value);
					}
				}
				return vectors;
			});
			List<double[]> vectors = new ArrayList<>();
			for (JsonNode item : response) {
				double[] vector = new double[item.size()];
				for (int index = 0; index < vector.length; index++) {
					vector[index] = item.get(index).asDouble();
				}
				vectors.add(vector);
			}
			return vectors;
		};
	}

}
