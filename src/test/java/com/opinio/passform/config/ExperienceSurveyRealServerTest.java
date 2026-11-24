package com.opinio.passform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 사용 경험 평가 설정이 꺼진 기본 서버(AC95). 실제 Tomcat에 HTTP로 보낸다(CsrfRealServerTest와 같은 방식).
// 평가 저장 경로는 POST만 공개이고 CSRF는 켜져 있다. 꺼진 서버도 새 제출의 201에 experience_token: null을 늘 넣는다(SPEC 5절 195행).
class ExperienceSurveyRealServerTest extends DatabaseTest {

	private static final Pattern TOKEN = Pattern.compile("XSRF-TOKEN=([^;]+)");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String RATING = "{\"token\":\"t\",\"ease\":\"easy\"}";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository versions;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient client = HttpClient.newHttpClient();

	private String csrf;

	private UUID ownerId;

	private UUID formId;

	@BeforeEach
	void setUp() throws Exception {
		HttpResponse<String> first = send(HttpRequest.newBuilder(uri("/me")));
		Matcher matcher = TOKEN.matcher(first.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(value -> value.startsWith("XSRF-TOKEN="))
			.findFirst()
			.orElseThrow());
		assertThat(matcher.find()).isTrue();
		csrf = matcher.group(1);
	}

	@AfterEach
	void cleanUp() {
		if (formId != null) {
			jdbc.update("delete from experience_ratings where form_id = ?", formId);
			jdbc.update("delete from responses where form_id = ?", formId);
			jdbc.update("delete from form_versions where form_id = ?", formId);
			jdbc.update("delete from forms where form_id = ?", formId);
		}
		if (ownerId != null) {
			jdbc.update("delete from users where user_id = ?", ownerId);
		}
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder post(String path, String body) {
		return HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body));
	}

	private HttpRequest.Builder withCsrf(HttpRequest.Builder request) {
		return request.header("Cookie", "XSRF-TOKEN=" + csrf).header("X-XSRF-TOKEN", csrf);
	}

	private static String ratingPath() {
		return "/forms/" + UUID.randomUUID() + "/responses/" + UUID.randomUUID() + "/experience";
	}

	@Test
	void 비로그인_평가는_CSRF_토큰이_맞으면_보안_필터를_통과하고_꺼진_서버는_409_survey_closed다() throws Exception {
		HttpResponse<String> response = send(withCsrf(post(ratingPath(), RATING)));

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).isEqualTo("{\"error\":\"survey_closed\"}");
	}

	@Test
	void 비로그인_평가에_CSRF_토큰이_없거나_다르면_403이다() throws Exception {
		assertThat(send(post(ratingPath(), RATING)).statusCode()).isEqualTo(403);
		assertThat(send(post(ratingPath(), RATING).header("Cookie", "XSRF-TOKEN=" + csrf)
			.header("X-XSRF-TOKEN", "wrong-token")).statusCode()).isEqualTo(403);
	}

	@Test
	void 평가_경로의_GET은_공개하지_않아_비로그인이면_401이다() throws Exception {
		assertThat(send(HttpRequest.newBuilder(uri(ratingPath()))).statusCode()).isEqualTo(401);
	}

	@Test
	void 꺼진_서버의_새_제출_201에도_experience_token이_null로_들어간다() throws Exception {
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		Map<String, Object> definition = SampleForms.unbranched();
		Form form = new Form(ownerId, definition, TestData.NOW);
		ReflectionTestUtils.setField(form, "status", FormStatus.OPEN);
		ReflectionTestUtils.setField(form, "definitionVersion", 1);
		formId = forms.saveAndFlush(form).getId();
		versions.saveAndFlush(new FormVersion(formId, 1, definition, TestData.NOW));

		HttpResponse<String> response = send(withCsrf(post("/forms/" + formId + "/responses", """
				{"form_version":1,"answers":[{"question_id":"q1","value":"1학년"},{"question_id":"q2","value":"김가상"}],
				 "consent_agreed":true}
				""")));

		assertThat(response.statusCode()).isEqualTo(201);
		JsonNode body = JSON.readTree(response.body());
		assertThat(body.has("experience_token")).isTrue();
		assertThat(body.get("experience_token").isNull()).isTrue();
		assertThat(body.get("response_id").asString()).isNotBlank();
		assertThat(jdbc.queryForObject("select count(*) from experience_ratings where form_id = ?", Integer.class,
				formId)).isZero();
	}

}
