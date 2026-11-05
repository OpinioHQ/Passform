package com.opinio.passform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.opinio.passform.support.DatabaseTest;

// 실제 Tomcat에 HTTP로 보낸다. MockMvc는 오류 전달(/error)을 하지 않아 실제 서버와 응답이 달라질 수 있다.
class CsrfRealServerTest extends DatabaseTest {

	private static final Pattern TOKEN = Pattern.compile("XSRF-TOKEN=([^;]+)");

	private static final String BODY = "{\"form_version\":1,\"answers\":[],\"consent_agreed\":true}";

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	private String token;

	private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder request(String method, String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Content-Type", "application/json")
			.method(method, HttpRequest.BodyPublishers.ofString(BODY));
	}

	private static String submitPath() {
		return "/forms/" + UUID.randomUUID() + "/responses";
	}

	@BeforeEach
	void 비로그인_브라우저가_첫_응답에서_토큰을_받는다() throws Exception {
		HttpResponse<String> first = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me")));
		assertThat(first.statusCode()).isEqualTo(401);
		String cookie = first.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(value -> value.startsWith("XSRF-TOKEN="))
			.findFirst()
			.orElseThrow();
		assertThat(cookie).doesNotContainIgnoringCase("HttpOnly").contains("SameSite=Lax");
		Matcher matcher = TOKEN.matcher(cookie);
		assertThat(matcher.find()).isTrue();
		token = matcher.group(1);
	}

	@Test
	void 비로그인_제출에_토큰이_없으면_403이다() throws Exception {
		assertThat(send(request("POST", submitPath())).statusCode()).isEqualTo(403);
		assertThat(send(request("POST", submitPath()).header("Cookie", "XSRF-TOKEN=" + token)).statusCode())
			.isEqualTo(403);
	}

	@Test
	void 비로그인_제출에_토큰이_다르면_403이다() throws Exception {
		assertThat(send(request("POST", submitPath()).header("Cookie", "XSRF-TOKEN=" + token)
			.header("X-XSRF-TOKEN", "wrong-token")).statusCode()).isEqualTo(403);
	}

	// 없는 폼이므로 보안 필터를 모두 통과하면 not_found다. 제출 API가 있어도 없어도 같다.
	@Test
	void 비로그인_제출에_토큰이_맞으면_CSRF와_로그인_검사를_통과한다() throws Exception {
		HttpResponse<String> response = send(request("POST", submitPath()).header("Cookie", "XSRF-TOKEN=" + token)
			.header("X-XSRF-TOKEN", token));

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.body()).isEqualTo("{\"error\":\"not_found\"}");
	}

	@Test
	void 로그인이_필요한_변경_요청은_토큰이_맞아도_비로그인이면_401이다() throws Exception {
		assertThat(send(request("PATCH", "/me/settings").header("Cookie", "XSRF-TOKEN=" + token)
			.header("X-XSRF-TOKEN", token)).statusCode()).isEqualTo(401);
	}

}
