package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.StorageQuotaService;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.MemoryFileStorage;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 제작자 첨부와 사용량(SPEC 5절 100행, 121~124행, 169~172행, 196~197행, AC2, AC63, AC65)을 실제 Postgres로 시험한다.
// 트랜잭션을 테스트로 묶지 않는다. 커밋 뒤에만 일어나는 일(원본 삭제)을 봐야 하기 때문이다. 파일은 메모리 저장소다.
@Import(MemoryFileStorage.Config.class)
class AttachmentApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final long LIMIT = StorageQuotaService.LIMIT_BYTES;

	private static final byte[] PNG = png(100);

	private static final byte[] PDF = "%PDF-1.7 가상 문서".getBytes(java.nio.charset.StandardCharsets.UTF_8);

	private static final byte[] MP4 = "\0\0\0\u0018ftypmp42 가상".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

	private static final byte[] EXE = "MZ가상 실행 파일".getBytes(java.nio.charset.StandardCharsets.UTF_8);

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MemoryFileStorage storage;

	@Autowired
	private UserRepository users;

	@Autowired
	private AttachmentRepository attachments;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private FormVersionRepository versions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MultipartProperties multipartProperties;

	@MockitoSpyBean
	private StorageQuotaService quota;

	private MockMvc mockMvc;

	private UUID owner;

	private UUID other;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		storage.reset();
		owner = users.save(TestData.newUser()).getId();
		other = users.save(TestData.newUser()).getId();
	}

	// 커밋을 보는 테스트라 행이 남는다. DB 전체 행 수를 세는 다른 테스트에 영향을 주지 않게 지운다.
	@AfterEach
	void removeCommittedRows() {
		jdbc.update("delete from attachments");
		jdbc.update("delete from responses");
	}

	private static byte[] png(int size) {
		byte[] bytes = new byte[size];
		byte[] signature = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		System.arraycopy(signature, 0, bytes, 0, signature.length);
		return bytes;
	}

	// 요청 도우미

	private MockHttpServletResponse perform(AbstractMockHttpServletRequestBuilder<?> request, UUID actor) throws Exception {
		if (actor != null) {
			request.with(user(actor.toString()));
		}
		return mockMvc.perform(request.with(csrf())).andReturn().getResponse();
	}

	private String createForm(UUID actor) throws Exception {
		MockHttpServletResponse response = perform(post("/forms").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(SampleForms.unbranched())), actor);
		assertThat(response.getStatus()).isEqualTo(201);
		return JSON.readTree(response.getContentAsByteArray()).get("form_id").asString();
	}

	private String openForm(UUID actor) throws Exception {
		String formId = createForm(actor);
		assertThat(patchForm(formId, actor, Map.of("status", "open")).getStatus()).isEqualTo(200);
		return formId;
	}

	private MockHttpServletResponse patchForm(String formId, UUID actor, Object body) throws Exception {
		return perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),
				actor);
	}

	private MockHttpServletResponse upload(String path, UUID actor, String filename, String clientType, byte[] content)
			throws Exception {
		return perform(multipart(path).file(new MockMultipartFile("file", filename, clientType, content)), actor);
	}

	private MockHttpServletResponse uploadNote(String formId, UUID actor, String filename, byte[] content) throws Exception {
		return upload("/forms/" + formId + "/attachments", actor, filename, "application/octet-stream", content);
	}

	private MockHttpServletResponse uploadQuestion(String formId, UUID actor, String filename, byte[] content)
			throws Exception {
		return upload("/forms/" + formId + "/questions/q1/attachments", actor, filename, "application/octet-stream", content);
	}

	private String idOf(MockHttpServletResponse created) throws Exception {
		assertThat(created.getStatus()).isEqualTo(201);
		return JSON.readTree(created.getContentAsByteArray()).get("attachment_id").asString();
	}

	private JsonNode definition(String formId) throws Exception {
		return JSON.readTree(perform(get("/forms/" + formId + "/definition"), owner).getContentAsByteArray());
	}

	private long used(UUID actor) throws Exception {
		return JSON.readTree(perform(get("/me/storage"), actor).getContentAsByteArray()).get("used_bytes").asLong();
	}

	private static void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
		assertThat(response.getStatus()).isEqualTo(status);
		assertThat(JSON.readTree(response.getContentAsByteArray()).get("error").asString()).isEqualTo(code);
	}

	private Map<String, Object> versionDefinition(String formId, int version) {
		return versions.findById(new FormVersionId(UUID.fromString(formId), version)).orElseThrow().getDefinition();
	}

	private long versionCount(String formId) {
		return versions.findByFormId(UUID.fromString(formId)).size();
	}

	// 실제로 500MB를 보내지 않고 받은 응답의 첨부 행으로 사용량을 target까지 채운다.
	private void fillUsageTo(UUID actor, String formId, long target) throws Exception {
		Response filler = responses.save(TestData.submitted(UUID.fromString(formId), null, 1, TestData.NOW));
		long remaining = target - used(actor);
		assertThat(remaining).isPositive();
		attachments.save(new Attachment(UUID.fromString(formId), filler.getId(), "q1", "채움.zip", "application/zip", remaining,
				"filler/" + UUID.randomUUID(), TestData.NOW));
		assertThat(used(actor)).isEqualTo(target);
	}

	// 파일 하나를 초안에 붙일 때 늘어나는 바이트(파일 + 정의 JSON).
	private long uploadCost(String formId) throws Exception {
		long before = used(owner);
		String id = idOf(uploadNote(formId, owner, "a.png", PNG));
		long cost = used(owner) - before;
		assertThat(perform(delete("/forms/" + formId + "/attachments/" + id), owner).getStatus()).isEqualTo(204);
		storage.awaitObjectCount(0);
		assertThat(used(owner)).isEqualTo(before);
		return cost;
	}

	private void assertUnchanged(String formId, int versionCount, long usedBefore, int objects) throws Exception {
		assertThat(versionCount(formId)).isEqualTo(versionCount);
		assertThat(used(owner)).isEqualTo(usedBefore);
		assertThat(storage.objectCount()).isEqualTo(objects);
		assertThat(definition(formId).get("note_attachments")).isEmpty();
		assertThat(definition(formId).at("/sections/0/questions/0/attachments")).isEmpty();
	}

	// 첨부와 버전

	@Test
	void AC63_draft_attachment_keeps_version_초안_첨부는_작업_정의만_바꾸고_버전을_만들지_않는다() throws Exception {
		String formId = createForm(owner);

		String id = idOf(uploadNote(formId, owner, "a.png", PNG));
		String questionFile = idOf(uploadQuestion(formId, owner, "b.pdf", PDF));

		JsonNode body = definition(formId);
		assertThat(body.get("definition_version").isNull()).isTrue();
		assertThat(body.get("note_attachments").get(0).get("attachment_id").asString()).isEqualTo(id);
		assertThat(body.at("/sections/0/questions/0/attachments/0/attachment_id").asString()).isEqualTo(questionFile);
		assertThat(versionCount(formId)).isZero();
		assertThat(storage.objectCount()).isEqualTo(2);

		assertThat(perform(delete("/forms/" + formId + "/attachments/" + id), owner).getStatus()).isEqualTo(204);
		storage.awaitObjectCount(1);
		assertThat(definition(formId).get("note_attachments")).isEmpty();
		assertThat(attachments.findById(UUID.fromString(id))).isEmpty();
	}

	@Test
	void AC63_업로드_응답과_저장값은_판별한_형식이고_S3_키에_파일_이름이_없다() throws Exception {
		String formId = createForm(owner);

		MockHttpServletResponse response = upload("/forms/" + formId + "/attachments", owner, "비밀 안내문.png", "text/html", PNG);

		assertThat(response.getStatus()).isEqualTo(201);
		JsonNode body = JSON.readTree(response.getContentAsByteArray());
		assertThat(body.get("filename").asString()).isEqualTo("비밀 안내문.png");
		assertThat(body.get("content_type").asString()).isEqualTo("image/png");
		assertThat(body.get("size_bytes").asLong()).isEqualTo(PNG.length);
		Attachment saved = attachments.findById(UUID.fromString(body.get("attachment_id").asString())).orElseThrow();
		assertThat(saved.getContentType()).isEqualTo("image/png");
		assertThat(saved.getS3Key()).doesNotContain("비밀").doesNotContain(".png");
		assertThat(storage.has(saved.getS3Key())).isTrue();
	}

	@Test
	void AC63_attachment_api_version_atomic_배포_폼의_첨부_추가는_새_불변_버전이다() throws Exception {
		String formId = openForm(owner);
		Map<String, Object> first = versionDefinition(formId, 1);

		String id = idOf(uploadNote(formId, owner, "a.png", PNG));

		assertThat(definition(formId).get("definition_version").asInt()).isEqualTo(2);
		assertThat(versionDefinition(formId, 1)).isEqualTo(first);
		assertThat(JSON.valueToTree(versionDefinition(formId, 2)).get("note_attachments").get(0).get("attachment_id").asString())
			.isEqualTo(id);
	}

	@Test
	void AC63_attachment_change_creates_immutable_version_첨부_제거도_새_버전이고_과거_버전의_원본과_사용량을_유지한다() throws Exception {
		String formId = openForm(owner);
		String id = idOf(uploadNote(formId, owner, "a.png", PNG));
		long usedWithFile = used(owner);

		assertThat(perform(delete("/forms/" + formId + "/attachments/" + id), owner).getStatus()).isEqualTo(204);

		assertThat(definition(formId).get("definition_version").asInt()).isEqualTo(3);
		assertThat(definition(formId).get("note_attachments")).isEmpty();
		assertThat(JSON.valueToTree(versionDefinition(formId, 2)).get("note_attachments").get(0).get("attachment_id").asString())
			.isEqualTo(id);
		assertThat(attachments.findById(UUID.fromString(id))).isPresent();
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(storage.deleteCount()).isZero();
		// 파일은 그대로 세고 버전 3의 정의만큼 늘어난다.
		assertThat(used(owner)).isGreaterThanOrEqualTo(usedWithFile);
	}

	@Test
	void 없는_문항이나_목록에_없는_첨부는_404다() throws Exception {
		String formId = createForm(owner);
		String id = idOf(uploadQuestion(formId, owner, "a.png", PNG));

		assertError(upload("/forms/" + formId + "/questions/nope/attachments", owner, "a.png", "image/png", PNG), 404, "not_found");
		assertError(perform(delete("/forms/" + formId + "/attachments/" + id), owner), 404, "not_found");
		assertError(perform(delete("/forms/" + formId + "/questions/q2/attachments/" + id), owner), 404, "not_found");
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(storage.deleteCount()).isZero();
	}

	// 실패한 요청은 아무것도 남기지 않는다

	@Test
	void AC63_creator_attachment_rejects_type_확장자만_PDF인_실행_파일은_400이고_목록_버전_사용량_객체가_그대로다() throws Exception {
		String formId = openForm(owner);
		long before = used(owner);

		assertError(uploadNote(formId, owner, "a.pdf", EXE), 400, "invalid_file_type");
		assertError(uploadQuestion(formId, owner, "a.zip", new byte[] { 0x50, 0x4B, 0x03, 0x04 }), 400, "invalid_file_type");

		assertThat(storage.putCount()).isZero();
		assertUnchanged(formId, 1, before, 0);
	}

	@Test
	void S3_저장이_실패하면_503이고_목록_버전_사용량_객체가_그대로다() throws Exception {
		String formId = openForm(owner);
		long before = used(owner);
		storage.failPuts();

		assertError(uploadNote(formId, owner, "a.png", PNG), 503, "storage_unavailable");

		assertUnchanged(formId, 1, before, 0);
	}

	@Test
	void S3가_저장한_뒤_put이_예외로_끝나도_원본을_지운다() throws Exception {
		String formId = openForm(owner);
		long before = used(owner);
		storage.failAfterStoring();

		assertError(uploadNote(formId, owner, "a.png", PNG), 503, "storage_unavailable");

		assertThat(storage.deleteCount()).isEqualTo(1);
		assertUnchanged(formId, 1, before, 0);
	}

	@Test
	void S3가_복사한_뒤_copy가_예외로_끝나도_복사한_원본을_지운다() throws Exception {
		String formId = createForm(owner);
		idOf(uploadNote(formId, owner, "a.png", PNG));
		long forms = createdFormCount();
		storage.failAfterStoring();

		assertError(copy(formId, owner), 503, "storage_unavailable");

		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(createdFormCount()).isEqualTo(forms);
	}

	@Test
	void DB_저장이_실패하면_올린_원본을_지우고_목록_버전_사용량_행이_그대로다() throws Exception {
		String formId = openForm(owner);
		long before = used(owner);
		long rowsBefore = attachments.count();
		doThrow(new IllegalStateException("가상 DB 실패")).when(quota).requireWithinLimit(any(), anyLong());

		assertThat(uploadNote(formId, owner, "a.png", PNG).getStatus()).isEqualTo(500);

		assertThat(storage.putCount()).isEqualTo(1);
		assertThat(attachments.count()).isEqualTo(rowsBefore);
		org.mockito.Mockito.reset(quota);
		assertUnchanged(formId, 1, before, 0);
	}

	@Test
	void 첨부_제거의_DB가_실패하면_S3_삭제를_부르지_않고_모두_그대로다() throws Exception {
		String formId = createForm(owner);
		String id = idOf(uploadNote(formId, owner, "a.png", PNG));
		doThrow(new IllegalStateException("가상 DB 실패")).when(quota).requireWithinLimit(any(), anyLong());

		assertThat(perform(delete("/forms/" + formId + "/attachments/" + id), owner).getStatus()).isEqualTo(500);

		org.mockito.Mockito.reset(quota);
		Thread.sleep(100);
		assertThat(storage.deleteCount()).isZero();
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(attachments.findById(UUID.fromString(id))).isPresent();
		assertThat(definition(formId).get("note_attachments").get(0).get("attachment_id").asString()).isEqualTo(id);
	}

	// 권한

	@Test
	void AC2_제작자가_아니면_첨부_추가_제거가_403이고_비로그인은_401이다() throws Exception {
		String formId = createForm(owner);
		String id = idOf(uploadNote(formId, owner, "a.png", PNG));

		assertError(uploadNote(formId, other, "b.png", PNG), 403, "forbidden");
		assertError(perform(delete("/forms/" + formId + "/attachments/" + id), other), 403, "forbidden");
		assertThat(uploadNote(formId, null, "b.png", PNG).getStatus()).isEqualTo(401);
		assertThat(perform(delete("/forms/" + formId + "/attachments/" + id), null).getStatus()).isEqualTo(401);

		assertThat(storage.putCount()).isEqualTo(1);
		assertThat(storage.deleteCount()).isZero();
		assertThat(attachments.findById(UUID.fromString(id))).isPresent();
	}

	@Test
	void 삭제된_폼에는_첨부를_붙이거나_뺄_수_없다() throws Exception {
		String formId = createForm(owner);
		String id = idOf(uploadNote(formId, owner, "a.png", PNG));
		jdbc.update("update forms set deleted_at = now() where form_id = ?", UUID.fromString(formId));

		assertError(uploadNote(formId, owner, "b.png", PNG), 404, "not_found");
		assertError(perform(delete("/forms/" + formId + "/attachments/" + id), owner), 404, "not_found");
		assertThat(storage.objectCount()).isEqualTo(1);
	}

	// 다운로드

	private MockHttpServletResponse download(String path, UUID actor) throws Exception {
		return perform(get(path), actor);
	}

	private static void assertRedirected(MockHttpServletResponse response) {
		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getRedirectedUrl()).startsWith("https://memory.test/attachments/");
		assertThat(response.getHeader("Cache-Control")).contains("no-store");
	}

	@Test
	void AC63_현재_버전의_열린_폼_첨부는_비로그인도_받는다() throws Exception {
		String formId = openForm(owner);
		String note = idOf(uploadNote(formId, owner, "a.png", PNG));
		String question = idOf(uploadQuestion(formId, owner, "b.pdf", PDF));

		assertRedirected(download("/forms/" + formId + "/attachments/" + note, null));
		assertRedirected(download("/forms/" + formId + "/questions/q1/attachments/" + question, null));
	}

	@Test
	void AC63_이미지만_바로_열고_PDF와_MP4는_내려받는다() throws Exception {
		String formId = openForm(owner);
		String image = idOf(uploadNote(formId, owner, "a.png", PNG));
		String pdf = idOf(uploadNote(formId, owner, "b.pdf", PDF));
		String mp4 = idOf(uploadNote(formId, owner, "c.mp4", MP4));

		assertThat(download("/forms/" + formId + "/attachments/" + image, null).getRedirectedUrl()).contains("inline=true");
		assertThat(download("/forms/" + formId + "/attachments/" + pdf, null).getRedirectedUrl())
			.contains("inline=false")
			.contains("application%2Fpdf");
		assertThat(download("/forms/" + formId + "/attachments/" + mp4, null).getRedirectedUrl())
			.contains("inline=false")
			.contains("video%2Fmp4");
	}

	@Test
	void AC63_question_attachment_owner_only_draft_초안의_첨부는_제작자만_받는다() throws Exception {
		String formId = createForm(owner);
		String path = "/forms/" + formId + "/questions/q1/attachments/" + idOf(uploadQuestion(formId, owner, "a.png", PNG));

		assertRedirected(download(path, owner));
		assertThat(download(path, null).getStatus()).isEqualTo(401);
		assertError(download(path, other), 403, "forbidden");
	}

	@Test
	void AC63_과거_버전_파일은_제작자와_그_버전_제출자만_받는다() throws Exception {
		String formId = openForm(owner);
		String id = idOf(uploadNote(formId, owner, "a.png", PNG));
		String path = "/forms/" + formId + "/attachments/" + id;
		UUID submitter = users.save(TestData.newUser()).getId();
		UUID laterSubmitter = users.save(TestData.newUser()).getId();
		responses.save(TestData.submitted(UUID.fromString(formId), submitter, 2, TestData.NOW));
		assertThat(perform(delete(path), owner).getStatus()).isEqualTo(204);
		responses.save(TestData.submitted(UUID.fromString(formId), laterSubmitter, 3, TestData.NOW));

		assertRedirected(download(path, owner));
		assertRedirected(download(path, submitter));
		assertError(download(path, laterSubmitter), 404, "not_found");
		assertError(download(path, other), 404, "not_found");
		assertThat(download(path, null).getStatus()).isEqualTo(401);
	}

	@Test
	void AC63_닫힌_폼의_첨부는_제작자와_제출자만_받는다() throws Exception {
		String formId = openForm(owner);
		String path = "/forms/" + formId + "/attachments/" + idOf(uploadNote(formId, owner, "a.png", PNG));
		UUID submitter = users.save(TestData.newUser()).getId();
		responses.save(TestData.submitted(UUID.fromString(formId), submitter, 2, TestData.NOW));
		assertThat(patchForm(formId, owner, Map.of("status", "closed")).getStatus()).isEqualTo(200);

		assertRedirected(download(path, owner));
		assertRedirected(download(path, submitter));
		assertThat(download(path, null).getStatus()).isEqualTo(401);
		assertError(download(path, other), 404, "not_found");
	}

	@Test
	void AC63_경로의_폼_문항과_맞지_않는_attachment_id는_404다() throws Exception {
		String formId = openForm(owner);
		String otherForm = openForm(owner);
		String note = idOf(uploadNote(formId, owner, "a.png", PNG));
		String question = idOf(uploadQuestion(formId, owner, "b.png", PNG));

		assertError(download("/forms/" + otherForm + "/attachments/" + note, owner), 404, "not_found");
		assertError(download("/forms/" + formId + "/attachments/" + question, null), 404, "not_found");
		assertError(download("/forms/" + formId + "/questions/q1/attachments/" + note, null), 404, "not_found");
		assertError(download("/forms/" + formId + "/questions/q2/attachments/" + question, null), 404, "not_found");
		assertError(download("/forms/" + formId + "/attachments/not-a-uuid", null), 404, "not_found");
	}

	@Test
	void 응답에_연결된_파일은_폼_첨부_경로로_받을_수_없다() throws Exception {
		String formId = openForm(owner);
		Response response = responses.save(TestData.submitted(UUID.fromString(formId), other, 1, TestData.NOW));
		Attachment file = attachments.save(new Attachment(UUID.fromString(formId), response.getId(), "q1", "a.png", "image/png", 10,
				"attachments/r", TestData.NOW));

		assertError(download("/forms/" + formId + "/questions/q1/attachments/" + file.getId(), owner), 404, "not_found");
		assertError(download("/forms/" + formId + "/attachments/" + file.getId(), owner), 404, "not_found");
	}

	@Test
	void AC63_삭제된_폼의_첨부는_404다() throws Exception {
		String formId = openForm(owner);
		String path = "/forms/" + formId + "/attachments/" + idOf(uploadNote(formId, owner, "a.png", PNG));
		jdbc.update("update forms set deleted_at = now() where form_id = ?", UUID.fromString(formId));

		assertError(download(path, owner), 404, "not_found");
		assertError(download(path, null), 404, "not_found");
	}

	// PATCH와 첨부 목록

	@Test
	void PATCH의_오래된_첨부_목록은_무시하고_서버_목록을_유지한다() throws Exception {
		String formId = createForm(owner);
		String note = idOf(uploadNote(formId, owner, "a.png", PNG));
		String question = idOf(uploadQuestion(formId, owner, "b.png", PNG));
		Map<String, Object> stale = SampleForms.unbranched();

		assertThat(patchForm(formId, owner, Map.of("title", "새 제목", "note_attachments", stale.get("note_attachments"),
				"sections", stale.get("sections"))).getStatus()).isEqualTo(200);

		JsonNode body = definition(formId);
		assertThat(body.get("title").asString()).isEqualTo("새 제목");
		assertThat(body.get("note_attachments").get(0).get("attachment_id").asString()).isEqualTo(note);
		assertThat(body.at("/sections/0/questions/0/attachments/0/attachment_id").asString()).isEqualTo(question);
	}

	@Test
	void PATCH와_새_폼으로_다른_폼의_attachment_id를_끌어올_수_없다() throws Exception {
		String victim = createForm(other);
		String foreign = idOf(uploadNote(victim, other, "a.png", PNG));
		String formId = createForm(owner);
		List<Map<String, Object>> smuggled = List.of(Map.of("attachment_id", foreign, "filename", "a.png", "content_type", "image/png", "size_bytes", 1));

		assertThat(patchForm(formId, owner, Map.of("note_attachments", smuggled)).getStatus()).isEqualTo(200);
		assertThat(definition(formId).get("note_attachments")).isEmpty();

		Map<String, Object> created = SampleForms.unbranched();
		created.put("note_attachments", smuggled);
		MockHttpServletResponse response = perform(post("/forms").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(created)), owner);
		String createdId = JSON.readTree(response.getContentAsByteArray()).get("form_id").asString();
		assertThat(definition(createdId).get("note_attachments")).isEmpty();
		assertError(download("/forms/" + formId + "/attachments/" + foreign, owner), 404, "not_found");
	}

	@Test
	void PATCH로_문항을_지우면_참조가_없어진_첨부를_정리한다() throws Exception {
		String formId = createForm(owner);
		String question = idOf(uploadQuestion(formId, owner, "a.png", PNG));
		Map<String, Object> changed = SampleForms.unbranched();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> sections = (List<Map<String, Object>>) changed.get("sections");
		sections.get(0).put("questions", new ArrayList<>(((List<?>) sections.get(0).get("questions")).subList(1, 3)));

		assertThat(patchForm(formId, owner, Map.of("sections", sections)).getStatus()).isEqualTo(200);

		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
		assertThat(attachments.findById(UUID.fromString(question))).isEmpty();
	}

	@Test
	void 배포_폼에서_문항을_지워도_과거_버전이_참조하는_원본은_남는다() throws Exception {
		String formId = openForm(owner);
		String question = idOf(uploadQuestion(formId, owner, "a.png", PNG));
		Map<String, Object> changed = SampleForms.unbranched();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> sections = (List<Map<String, Object>>) changed.get("sections");
		sections.get(0).put("questions", new ArrayList<>(((List<?>) sections.get(0).get("questions")).subList(1, 3)));

		assertThat(patchForm(formId, owner, Map.of("sections", sections)).getStatus()).isEqualTo(200);

		Thread.sleep(100);
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(attachments.findById(UUID.fromString(question))).isPresent();
	}

	// 사용량

	@Test
	void AC65_storage_quota_at_limit_정확히_500MB는_허용하고_1바이트라도_늘리면_409다() throws Exception {
		String formId = createForm(owner);
		long cost = uploadCost(formId);
		fillUsageTo(owner, formId, LIMIT - cost);

		idOf(uploadNote(formId, owner, "a.png", PNG));
		assertThat(used(owner)).isEqualTo(LIMIT);

		long objects = storage.objectCount();
		assertError(uploadNote(formId, owner, "b.png", PNG), 409, "storage_quota_exceeded");
		assertThat(used(owner)).isEqualTo(LIMIT);
		assertThat(storage.objectCount()).isEqualTo(objects);
	}

	@Test
	void AC65_storage_quota_rejects_overflow_1바이트_초과는_잠금_안_최종_판정에서_409이고_올린_원본을_지운다() throws Exception {
		String formId = createForm(owner);
		long cost = uploadCost(formId);
		fillUsageTo(owner, formId, LIMIT - cost + 1);
		int putsBefore = storage.putCount();
		long before = used(owner);

		assertError(uploadNote(formId, owner, "a.png", PNG), 409, "storage_quota_exceeded");

		// 파일 크기만 보는 사전 검사는 통과해 실제로 올렸다가 지웠다.
		assertThat(storage.putCount()).isEqualTo(putsBefore + 1);
		assertThat(storage.objectCount()).isZero();
		assertThat(used(owner)).isEqualTo(before);
		assertThat(definition(formId).get("note_attachments")).isEmpty();
	}

	@Test
	void 파일만으로_한도를_넘으면_S3에_올리지_않고_409다() throws Exception {
		String formId = createForm(owner);
		fillUsageTo(owner, formId, LIMIT - PNG.length + 1);

		assertError(uploadNote(formId, owner, "a.png", PNG), 409, "storage_quota_exceeded");

		assertThat(storage.putCount()).isZero();
	}

	@Test
	void AC65_배포_폼의_첨부_추가가_한도를_넘으면_버전을_만들지_않는다() throws Exception {
		String formId = openForm(owner);
		fillUsageTo(owner, formId, LIMIT);
		long before = used(owner);

		assertError(uploadNote(formId, owner, "a.png", PNG), 409, "storage_quota_exceeded");

		assertUnchanged(formId, 1, before, 0);
	}

	@Test
	void AC65_폼_생성이_한도를_넘으면_409이고_폼이_생기지_않는다() throws Exception {
		String formId = createForm(owner);
		fillUsageTo(owner, formId, LIMIT);
		long before = used(owner);

		assertError(perform(post("/forms").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(SampleForms.unbranched())), owner), 409,
				"storage_quota_exceeded");

		assertThat(used(owner)).isEqualTo(before);
		assertThat(JSON.readTree(perform(get("/me/created-forms"), owner).getContentAsByteArray())).hasSize(1);
	}

	@Test
	void AC65_폼_수정이_사용량을_늘려_한도를_넘으면_409이고_줄이는_수정은_된다() throws Exception {
		String formId = createForm(owner);
		fillUsageTo(owner, formId, LIMIT);

		assertError(patchForm(formId, owner, Map.of("title", "훨씬 더 길어진 가상 신청 제목")), 409, "storage_quota_exceeded");
		assertThat(definition(formId).get("title").asString()).isEqualTo("가상 신청");
		assertThat(used(owner)).isEqualTo(LIMIT);

		assertThat(patchForm(formId, owner, Map.of("title", "짧게")).getStatus()).isEqualTo(200);
		assertThat(used(owner)).isLessThan(LIMIT);
	}

	@Test
	void AC65_동시_저장_두_건이_합쳐_한도를_넘으면_하나만_성공하고_500이_없다() throws Exception {
		String formId = createForm(owner);
		long cost = uploadCost(formId);
		fillUsageTo(owner, formId, LIMIT - cost - cost / 2);
		storage.holdPutsUntil(2);
		Callable<Integer> call = () -> uploadNote(formId, owner, "a.png", PNG).getStatus();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<Integer>> results = pool.invokeAll(List.of(call, call));
		pool.shutdown();

		int[] statuses = { results.get(0).get(), results.get(1).get() };
		Arrays.sort(statuses);
		assertThat(statuses).containsExactly(201, 409);
		storage.awaitObjectCount(1);
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(used(owner)).isLessThanOrEqualTo(LIMIT);
		assertThat(definition(formId).get("note_attachments")).hasSize(1);
	}

	@Test
	void 응답_파일은_제작자에게만_세고_취소로_끊긴_행은_어디에도_세지_않고_받을_수_없다() throws Exception {
		String formId = openForm(owner);
		long ownerBefore = used(owner);
		long otherBefore = used(other);
		Response response = responses.save(TestData.submitted(UUID.fromString(formId), other, 1, TestData.NOW));
		attachments.save(new Attachment(UUID.fromString(formId), response.getId(), "q1", "a.zip", "application/zip", 1000, "attachments/x", TestData.NOW));
		long contentBytes = used(owner) - ownerBefore - 1000;
		assertThat(used(other)).isEqualTo(otherBefore);

		// T10의 취소가 남기는 모양: response_id가 비고 question_id는 그대로다.
		Attachment detached = attachments.save(new Attachment(UUID.fromString(formId), null, "q1", "b.zip", "application/zip", 5000, "attachments/y", TestData.NOW));

		assertThat(used(owner)).isEqualTo(ownerBefore + contentBytes + 1000);
		assertError(download("/forms/" + formId + "/questions/q1/attachments/" + detached.getId(), owner), 404, "not_found");
	}

	@Test
	void AC65_작성_중_답은_응답자_사용량이고_제작자에게_세지_않는다() throws Exception {
		String formId = openForm(owner);
		long ownerBefore = used(owner);
		long otherBefore = used(other);

		responses.save(TestData.withStatus(UUID.fromString(formId), other, com.opinio.passform.domain.analysis.ResponseStatus.IN_PROGRESS));

		assertThat(used(owner)).isEqualTo(ownerBefore);
		assertThat(used(other)).isGreaterThan(otherBefore);
	}

	// 폼 복사

	private MockHttpServletResponse copy(String formId, UUID actor) throws Exception {
		return perform(post("/forms/" + formId + "/copy"), actor);
	}

	private long createdFormCount() throws Exception {
		return JSON.readTree(perform(get("/me/created-forms"), owner).getContentAsByteArray()).size();
	}

	@Test
	void AC40_복사는_첨부_원본도_새로_복사해_복사본에서_받을_수_있고_사용량이_는다() throws Exception {
		String formId = createForm(owner);
		String note = idOf(uploadNote(formId, owner, "a.png", PNG));
		String question = idOf(uploadQuestion(formId, owner, "b.pdf", PDF));
		long before = used(owner);

		MockHttpServletResponse response = copy(formId, owner);

		assertThat(response.getStatus()).isEqualTo(201);
		String copied = JSON.readTree(response.getContentAsByteArray()).get("form_id").asString();
		JsonNode body = definition(copied);
		String newNote = body.at("/note_attachments/0/attachment_id").asString();
		String newQuestion = body.at("/sections/0/questions/0/attachments/0/attachment_id").asString();
		assertThat(newNote).isNotEqualTo(note);
		assertThat(newQuestion).isNotEqualTo(question);
		assertThat(body.at("/note_attachments/0/filename").asString()).isEqualTo("a.png");
		assertThat(storage.objectCount()).isEqualTo(4);
		assertRedirected(download("/forms/" + copied + "/attachments/" + newNote, owner));
		assertRedirected(download("/forms/" + copied + "/questions/q1/attachments/" + newQuestion, owner));
		assertThat(used(owner)).isGreaterThan(before + PNG.length + PDF.length);
	}

	@Test
	void 원본의_첨부를_지워도_복사본의_파일은_남는다() throws Exception {
		String formId = createForm(owner);
		String note = idOf(uploadNote(formId, owner, "a.png", PNG));
		String copied = JSON.readTree(copy(formId, owner).getContentAsByteArray()).get("form_id").asString();
		String newNote = definition(copied).at("/note_attachments/0/attachment_id").asString();

		assertThat(perform(delete("/forms/" + formId + "/attachments/" + note), owner).getStatus()).isEqualTo(204);

		storage.awaitObjectCount(1);
		assertThat(storage.objectCount()).isEqualTo(1);
		assertRedirected(download("/forms/" + copied + "/attachments/" + newNote, owner));
	}

	@Test
	void AC65_복사가_한도를_넘으면_409이고_폼과_객체가_늘지_않는다() throws Exception {
		String formId = createForm(owner);
		idOf(uploadNote(formId, owner, "a.png", PNG));
		fillUsageTo(owner, formId, LIMIT - PNG.length + 1);
		long before = used(owner);
		int objects = storage.objectCount();
		long forms = createdFormCount();

		assertError(copy(formId, owner), 409, "storage_quota_exceeded");

		assertThat(used(owner)).isEqualTo(before);
		assertThat(storage.objectCount()).isEqualTo(objects);
		assertThat(createdFormCount()).isEqualTo(forms);
	}

	@Test
	void AC65_파일_복사_뒤_잠금_안_최종_판정이_실패하면_복사한_원본을_지운다() throws Exception {
		String formId = createForm(owner);
		idOf(uploadNote(formId, owner, "a.png", PNG));
		// 파일만 보는 사전 검사는 통과하고, 새 폼의 정의 JSON까지 세면 넘는다.
		fillUsageTo(owner, formId, LIMIT - PNG.length);
		int objects = storage.objectCount();
		long forms = createdFormCount();

		assertError(copy(formId, owner), 409, "storage_quota_exceeded");

		assertThat(storage.objectCount()).isEqualTo(objects);
		assertThat(createdFormCount()).isEqualTo(forms);
	}

	@Test
	void S3_복사가_실패하면_503이고_폼과_객체가_늘지_않는다() throws Exception {
		String formId = createForm(owner);
		idOf(uploadNote(formId, owner, "a.png", PNG));
		long forms = createdFormCount();
		storage.failPuts();

		assertError(copy(formId, owner), 503, "storage_unavailable");

		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(createdFormCount()).isEqualTo(forms);
	}

	@Test
	void AC2_남의_폼은_복사할_수_없고_첨부도_복사하지_않는다() throws Exception {
		String formId = createForm(owner);
		idOf(uploadNote(formId, owner, "a.png", PNG));

		assertError(copy(formId, other), 403, "forbidden");

		assertThat(storage.objectCount()).isEqualTo(1);
	}

	@Test
	void 사용량_API는_SPEC_모양이고_로그인이_필요하다() throws Exception {
		JsonNode body = JSON.readTree(perform(get("/me/storage"), owner).getContentAsByteArray());
		assertThat(body.get("used_bytes").asLong()).isZero();
		assertThat(body.get("limit_bytes").asLong()).isEqualTo(500_000_000L);
		assertThat(perform(get("/me/storage"), null).getStatus()).isEqualTo(401);
	}

	@Test
	void multipart_한도는_Spring_기본값이_아니라_서비스가_판정하게_넉넉하다() {
		assertThat(multipartProperties.getMaxFileSize()).isGreaterThan(DataSize.ofMegabytes(500));
		assertThat(multipartProperties.getMaxRequestSize()).isGreaterThan(DataSize.ofMegabytes(500));
	}

}
