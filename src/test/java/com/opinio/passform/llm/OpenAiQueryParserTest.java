package com.opinio.passform.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 로컬 스텁 서버로 요청 형태만 본다. 실제 OpenAI는 부르지 않는다. 키 값은 단언 메시지에 남기지 않는다.
class OpenAiQueryParserTest {

	private static final String TEST_KEY = "test-only-placeholder";

	private static final String OUTPUT = "{\"conditions\":[],\"operation\":\"count\"}";

	private static final String RESPONSE = """
			{"output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"{\\"conditions\\":[],\\"operation\\":\\"count\\"}"}]}],"usage":{"input_tokens":1,"output_tokens":1}}
			""";

	private final AtomicReference<String> authorization = new AtomicReference<>();

	private final AtomicReference<String> requestBody = new AtomicReference<>();

	private final AtomicReference<Integer> statusCode = new AtomicReference<>(200);

	private HttpServer server;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/responses", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] reply = RESPONSE.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(statusCode.get(), reply.length);
			exchange.getResponseBody().write(reply);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private OpenAiQueryParser parser(String key) {
		return new OpenAiQueryParser(key, "http://127.0.0.1:" + server.getAddress().getPort());
	}

	@Test
	void 모델과_스키마와_키_헤더를_맞춰_보내고_출력_문자열을_돌려준다() {
		String output = parser(TEST_KEY).parse("전체 응답 수 알려줘", null);

		assertThat(output).isEqualTo(OUTPUT);
		assertThat(("Bearer " + TEST_KEY).equals(authorization.get())).as("키 헤더가 맞아야 한다").isTrue();
		JsonNode body = JsonMapper.builder().build().readTree(requestBody.get());
		assertThat(body.path("model").asString()).isEqualTo("gpt-6-luna");
		assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("none");
		assertThat(body.path("temperature").asInt(-1)).isZero();
		assertThat(body.path("input").asString()).isEqualTo("전체 응답 수 알려줘");
		assertThat(body.path("instructions").asString()).startsWith("너는 설문 응답 분석 서비스의 요청 파서다")
			.endsWith("\"topic\":\"비용이 부담된다\"}")
			.doesNotContain("prompt_version");
		JsonNode format = body.path("text").path("format");
		assertThat(format.path("type").asString()).isEqualTo("json_schema");
		assertThat(format.path("schema").path("properties").has("conditions")).isTrue();
		assertThat(format.path("schema").has("$schema")).isFalse();
	}

	@Test
	void 다시_부를_때만_이전_오류를_붙인다() {
		parser(TEST_KEY).parse("질의", "오류 설명");

		assertThat(JsonMapper.builder().build().readTree(requestBody.get()).path("input").asString()).startsWith("질의")
			.contains("오류 설명");
	}

	@Test
	void 호출_실패는_llm_unavailable이다() {
		statusCode.set(500);

		assertUnavailable(parser(TEST_KEY));
	}

	@Test
	void 키가_없으면_호출하지_않고_llm_unavailable이다() {
		assertUnavailable(parser(null));
		assertUnavailable(parser(" "));
		assertThat(requestBody.get()).isNull();
	}

	private static void assertUnavailable(OpenAiQueryParser parser) {
		assertThatThrownBy(() -> parser.parse("질의", null)).isInstanceOf(DomainException.class)
			.extracting(thrown -> ((DomainException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNAVAILABLE);
	}

}
