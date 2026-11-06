package com.opinio.passform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;

import com.opinio.passform.support.DatabaseTest;

// SPEC 5절 API 표의 인증 열(AC2)을 실제 Tomcat에 HTTP로 확인한다. SPEC에 공개 경로가 늘면 여기에 한 줄 더한다.
// 아직 API가 없는 경로는 보안 필터를 통과하면 404, 막히면 401이다.
class PublicPathRealServerTest extends DatabaseTest {

	private static final String FORM = "/forms/" + UUID.randomUUID();

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	private int anonymousGet(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	// 187행 폼 공개 조회, 196·197행 폼 설명란·문항 첨부. 제출(188행)은 CsrfRealServerTest가 본다.
	@ParameterizedTest
	@ValueSource(strings = { "/f/some-slug", "/forms/{id}", "/forms/{id}/attachments/a1",
			"/forms/{id}/questions/q1/attachments/a1" })
	void SPEC의_공개_GET은_비로그인도_보안_필터를_통과한다(String path) throws Exception {
		assertThat(anonymousGet(path.replace("/forms/{id}", FORM))).isNotEqualTo(401).isEqualTo(404);
	}

	// 제작자 전용(116, 125, 126, 234, 236행)과 응답 첨부(198행)는 열리지 않는다.
	@ParameterizedTest
	@ValueSource(strings = { "/forms/{id}/definition", "/forms/{id}/branch-graph", "/forms/{id}/qr",
			"/forms/{id}/results", "/forms/{id}/responses/r1", "/forms/{id}/responses/r1/attachments/a1",
			"/forms/{id}/attachments/a1/extra", "/f/some-slug/extra", "/me", "/me/forms" })
	void 로그인이나_제작자_경로는_비로그인이면_401이다(String path) throws Exception {
		assertThat(anonymousGet(path.replace("/forms/{id}", FORM))).isEqualTo(401);
	}

}
