package com.opinio.passform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.MemoryFileStorage;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

// 실제 Tomcat에 multipart를 보낸다. MockMvc는 multipart 한도와 CSRF 필터의 본문 처리를 지나지 않는다.
@Import(MemoryFileStorage.Config.class)
class MultipartRealServerTest extends DatabaseTest {

	private static final Pattern TOKEN = Pattern.compile("XSRF-TOKEN=([^;]+)");

	private static final String BOUNDARY = "passform-test-boundary";

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

	@Autowired
	private MemoryFileStorage storage;

	private final HttpClient client = HttpClient.newHttpClient();

	// 실제 서버 요청이라 행이 남는다. DB 전체 행 수를 세는 다른 테스트에 영향을 주지 않게 지운다.
	@AfterEach
	void removeCommittedRows() {
		jdbc.update("delete from attachments");
		jdbc.update("delete from responses");
	}

	private String token() throws Exception {
		HttpResponse<String> first = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me")).build(),
				HttpResponse.BodyHandlers.ofString());
		Matcher matcher = TOKEN.matcher(String.join(";", first.headers().allValues("Set-Cookie")));
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private UUID openForm() {
		UUID owner = users.save(TestData.newUser()).getId();
		Form form = forms.save(new Form(owner, SampleForms.unbranched(), TestData.NOW));
		form.moveTo(FormStatus.OPEN);
		form.recordPublishedVersion(1, UUID.randomUUID().toString(), null);
		forms.save(form);
		versions.save(new FormVersion(form.getId(), 1, SampleForms.unbranched(), TestData.NOW));
		// 저장된 마감은 없고 정의의 마감은 쓰지 않는다. 운영 설정의 정본은 forms 행이다.
		jdbc.update("update forms set status = 'OPEN' where form_id = ?", form.getId());
		return form.getId();
	}

	private static void part(ByteArrayOutputStream out, String header, byte[] content) throws Exception {
		out.write(("--" + BOUNDARY + "\r\n" + header + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(content);
		out.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] zip(int size) {
		byte[] bytes = new byte[size];
		bytes[0] = 0x50;
		bytes[1] = 0x4B;
		bytes[2] = 0x03;
		bytes[3] = 0x04;
		return bytes;
	}

	private static byte[] body(String filename, List<byte[]> files) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String data = JsonMapper.builder().build().writeValueAsString(java.util.Map.of("form_version", 1, "consent_agreed", true,
				"answers", List.of(java.util.Map.of("question_id", "q1", "value", "1학년"),
						java.util.Map.of("question_id", "q2", "value", "홍길동"))));
		part(out, "Content-Disposition: form-data; name=\"data\"\r\nContent-Type: application/json", data.getBytes(StandardCharsets.UTF_8));
		for (byte[] file : files) {
			part(out, "Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\nContent-Type: application/zip", file);
			part(out, "Content-Disposition: form-data; name=\"file_question_ids\"", "q5".getBytes(StandardCharsets.UTF_8));
		}
		out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	private HttpResponse<String> post(UUID formId, String token, byte[] body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/forms/" + formId + "/responses"))
			.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body));
		if (token != null) {
			request.header("Cookie", "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void 비로그인_multipart_제출은_토큰이_있으면_CSRF를_통과하고_없으면_403이다() throws Exception {
		storage.reset();
		UUID formId = openForm();
		byte[] body = body("안내.zip", List.of(zip(2_000_000)));

		assertThat(post(formId, null, body).statusCode()).isEqualTo(403);
		assertThat(storage.putCount()).isZero();

		HttpResponse<String> ok = post(formId, token(), body);
		assertThat(ok.statusCode()).isEqualTo(201);
		assertThat(ok.body()).contains("안내.zip");
		assertThat(storage.objectCount()).isEqualTo(1);
	}

	@Test
	void _25MB를_넘는_합계는_Spring_기본_한도가_아니라_413_file_too_large다() throws Exception {
		storage.reset();
		UUID formId = openForm();

		HttpResponse<String> response = post(formId, token(), body("a.zip", List.of(zip(20_000_000), zip(6_000_000))));

		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(response.body()).isEqualTo("{\"error\":\"file_too_large\"}");
		assertThat(storage.putCount()).isZero();
	}

	@Test
	void 긴_한글_파일_이름도_Tomcat_제한에_걸리지_않는다() throws Exception {
		storage.reset();
		UUID formId = openForm();

		HttpResponse<String> response = post(formId, token(), body("가".repeat(200) + ".zip", List.of(zip(100))));

		assertThat(response.statusCode()).isEqualTo(201);
	}

}
