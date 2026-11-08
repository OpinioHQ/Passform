package com.opinio.passform.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.analysis.RagSettings;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 로컬 스텁 서버로 요청 형태만 본다. 실제 OpenAI는 부르지 않는다.
class OpenAiClientTest {

	private static final String KEY = "test-only-placeholder";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final LlmRequest REQUEST = new LlmRequest("classify_themes", "gpt-6-luna", "지시", "<answers>\n[]\n</answers>",
			"theme_labels", Map.of("type", "object"), 0.0);

	private final List<String> bodies = new ArrayList<>();

	private final List<String> authorizations = new ArrayList<>();

	private final AtomicInteger status = new AtomicInteger(200);

	private HttpServer server;

	private String responsesReply = """
			{"output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"{\\"labels\\":[]}"}]}],"usage":{"input_tokens":1,"output_tokens":1}}
			""";

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/responses", exchange -> reply(exchange, responsesReply));
		server.createContext("/embeddings", exchange -> {
			String body = capture(exchange);
			int size = JSON.readTree(body).path("input").size();
			StringBuilder data = new StringBuilder();
			for (int index = size - 1; index >= 0; index--) {
				data.append(data.isEmpty() ? "" : ",").append("{\"index\":").append(index).append(",\"embedding\":[").append(index).append(",1]}");
			}
			send(exchange, "{\"data\":[" + data + "],\"usage\":{\"total_tokens\":1}}");
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private void reply(HttpExchange exchange, String reply) throws IOException {
		capture(exchange);
		send(exchange, reply);
	}

	private synchronized String capture(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		bodies.add(body);
		authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
		return body;
	}

	private void send(HttpExchange exchange, String reply) throws IOException {
		byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status.get(), bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private OpenAiClient client(String key) {
		return new OpenAiClient(key, "http://127.0.0.1:" + server.getAddress().getPort());
	}

	@Test
	void 분류_요청은_구조화_출력으로_보내고_출력_문자열을_돌려준다() {
		assertThat(client(KEY).respond(REQUEST)).isEqualTo("{\"labels\":[]}");

		JsonNode body = JSON.readTree(bodies.get(0));
		assertThat(("Bearer " + KEY).equals(authorizations.get(0))).as("키 헤더가 맞아야 한다").isTrue();
		assertThat(body.path("model").asString()).isEqualTo("gpt-6-luna");
		assertThat(body.path("instructions").asString()).isEqualTo("지시");
		assertThat(body.path("input").asString()).isEqualTo("<answers>\n[]\n</answers>");
		assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("none");
		assertThat(body.path("temperature").asInt(-1)).isZero();
		assertThat(body.path("store").asBoolean(true)).isFalse();
		assertThat(body.path("text").path("format").path("name").asString()).isEqualTo("theme_labels");
		assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
	}

	@Test
	void temperature가_없으면_보내지_않는다() {
		client(KEY).respond(new LlmRequest("summarize_theme", "gpt-6.1-terra", "지시", "입력", "summary_text", Map.of(), null));

		assertThat(JSON.readTree(bodies.get(0)).has("temperature")).isFalse();
	}

	@Test
	void 임베딩은_입력_순서대로_돌려주고_답만_보낸다() {
		List<double[]> vectors = client(KEY).embed("theme_evidence", List.of("첫째", "둘째"));

		JsonNode body = JSON.readTree(bodies.get(0));
		assertThat(body.path("model").asString()).isEqualTo("text-embedding-3-small");
		assertThat(body.path("input").get(0).asString()).isEqualTo("첫째");
		assertThat(body.path("input").size()).isEqualTo(2);
		assertThat(vectors.get(0)).containsExactly(0.0, 1.0);
		assertThat(vectors.get(1)).containsExactly(1.0, 1.0);
	}

	@Test
	void 임베딩은_묶음으로_나눠_보내고_순서를_유지한다() {
		List<String> texts = new ArrayList<>();
		for (int index = 0; index < 257; index++) {
			texts.add("답" + index);
		}

		List<double[]> vectors = client(KEY).embed("topic_evidence", texts);

		assertThat(bodies).hasSize(2);
		assertThat(vectors).hasSize(257);
	}

	@Test
	void 호출_실패는_llm_unavailable이다() {
		status.set(500);

		assertUnavailable(() -> client(KEY).respond(REQUEST));
		assertUnavailable(() -> client(KEY).embed("theme_evidence", List.of("답")));
	}

	@Test
	void 키가_없으면_호출하지_않고_llm_unavailable이다() {
		assertUnavailable(() -> client(null).respond(REQUEST));
		assertUnavailable(() -> client(" ").embed("theme_evidence", List.of("답")));
		assertThat(bodies).isEmpty();
	}

	@Test
	void 프롬프트_파일과_rag_설정을_읽는다() {
		RagSettings settings = RagConfiguration.load();

		assertThat(settings).isEqualTo(new RagSettings(3, 0.2, 3, 0.35, 50, 300, 6, 200));
		assertThat(PromptData.promptOf("response_analysis/prompts/classify_themes.md")).startsWith("너는 설문 주관식 답의 주제 분류기다")
			.doesNotContain("MVP 임시본");
		assertThat(PromptData.promptOf("response_analysis/prompts/summarize.md")).startsWith("너는 설문 응답 요약 작성자다");
	}

	private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNAVAILABLE);
	}

}
