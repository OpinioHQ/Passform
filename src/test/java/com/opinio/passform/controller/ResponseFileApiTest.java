package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.service.StorageQuotaService;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.MemoryFileStorage;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 응답 파일의 제출·수정·취소·다운로드와 사용량(SPEC 5절 172·188·194·198행, 221~222행, AC14, AC64, AC65, AC83)을 실제 Postgres로 시험한다.
// 트랜잭션을 테스트로 묶지 않는다. 파일은 메모리 저장소이고, 25MB 판정은 실제 크기의 바이트로 본다.
@Import(MemoryFileStorage.Config.class)
class ResponseFileApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final long LIMIT = StorageQuotaService.LIMIT_BYTES;

	private static final long MB = 1_000_000L;

	private static final byte[] PDF = "%PDF-1.7 가상 문서".getBytes(StandardCharsets.UTF_8);

	private static final byte[] EXE = "MZ가상 실행 파일".getBytes(StandardCharsets.UTF_8);

	// q2만 필수이고 이름 형식이다. q1은 선택지 필수, q5는 장문 선택 문항이다.
	private static final List<Map<String, Object>> ANSWERS = List.of(Map.of("question_id", "q1", "value", "1학년"),
			Map.of("question_id", "q2", "value", "홍길동"));

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
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private StorageQuotaService quota;

	private MockMvc mockMvc;

	private UUID owner;

	private UUID respondent;

	private UUID other;

	private String formId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		storage.reset();
		owner = users.save(TestData.newUser()).getId();
		respondent = users.save(TestData.newUser()).getId();
		other = users.save(TestData.newUser()).getId();
		formId = openForm(true, true);
	}

	// 커밋을 보는 테스트라 행이 남는다. DB 전체 행 수를 세는 다른 테스트에 영향을 주지 않게 지운다.
	@AfterEach
	void removeCommittedRows() {
		jdbc.update("delete from attachments");
		jdbc.update("delete from responses");
	}

	private MockHttpServletResponse perform(AbstractMockHttpServletRequestBuilder<?> request, UUID actor) throws Exception {
		if (actor != null) {
			request.with(user(actor.toString()));
		}
		return mockMvc.perform(request.with(csrf())).andReturn().getResponse();
	}

	private String openForm(boolean editable, boolean cancellable) throws Exception {
		Map<String, Object> definition = SampleForms.unbranched();
		@SuppressWarnings("unchecked")
		Map<String, Object> settings = (Map<String, Object>) definition.get("settings");
		settings.put("editable", editable);
		settings.put("cancellable", cancellable);
		MockHttpServletResponse created = perform(post("/forms").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(definition)), owner);
		String id = JSON.readTree(created.getContentAsByteArray()).get("form_id").asString();
		perform(patch("/forms/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"open\"}"), owner);
		return id;
	}

	private static byte[] zip(long size) {
		byte[] bytes = new byte[(int) size];
		bytes[0] = 0x50;
		bytes[1] = 0x4B;
		bytes[2] = 0x03;
		bytes[3] = 0x04;
		return bytes;
	}

	private record Part(String questionId, String filename, byte[] content) {
	}

	private static Part pdf(String questionId) {
		return new Part(questionId, "제출.pdf", PDF);
	}

	private MockMultipartFile data(Map<String, Object> body) {
		return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(body));
	}

	private AbstractMockHttpServletRequestBuilder<?> withParts(org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request,
			Map<String, Object> body, Part... parts) {
		request.file(data(body));
		for (Part part : parts) {
			request.file(new MockMultipartFile("files", part.filename(), "text/plain", part.content()));
			request.param("file_question_ids", part.questionId());
		}
		return request;
	}

	private Map<String, Object> submitBody() {
		return Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true);
	}

	private MockHttpServletResponse submit(UUID actor, Map<String, Object> body, Part... parts) throws Exception {
		return perform(withParts(multipart("/forms/" + formId + "/responses"), body, parts), actor);
	}

	private MockHttpServletResponse submit(UUID actor, Part... parts) throws Exception {
		return submit(actor, submitBody(), parts);
	}

	private JsonNode created(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isEqualTo(201);
		return JSON.readTree(response.getContentAsByteArray());
	}

	private MockHttpServletResponse edit(UUID actor, String responseId, Map<String, Object> body, Part... parts) throws Exception {
		var request = multipart(HttpMethod.PATCH, "/me/responses/" + responseId);
		return perform(withParts(request, body, parts), actor);
	}

	private String downloadPath(String responseId, String attachmentId) {
		return "/forms/" + formId + "/responses/" + responseId + "/attachments/" + attachmentId;
	}

	private long used(UUID actor) throws Exception {
		return JSON.readTree(perform(get("/me/storage"), actor).getContentAsByteArray()).get("used_bytes").asLong();
	}

	private static void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
		assertThat(response.getStatus()).isEqualTo(status);
		assertThat(JSON.readTree(response.getContentAsByteArray()).get("error").asString()).isEqualTo(code);
	}

	private long submittedCount() {
		return responses.countSubmittedNotDeleted(UUID.fromString(formId));
	}

	private void assertNothingStored() {
		assertThat(submittedCount()).isZero();
		assertThat(attachments.count()).isEqualTo(attachmentsBefore);
		assertThat(storage.objectCount()).isZero();
	}

	private long attachmentsBefore;

	private void rememberCounts() {
		attachmentsBefore = attachments.count();
	}

	private void fillUsageTo(UUID actor, long target) throws Exception {
		String filler = openForm(false, false);
		Response response = responses.save(TestData.submitted(UUID.fromString(filler), null, 1, TestData.NOW));
		long remaining = target - used(actor);
		assertThat(remaining).isPositive();
		attachments.save(new Attachment(UUID.fromString(filler), response.getId(), "q1", "채움.zip", "application/zip", remaining,
				"filler/" + UUID.randomUUID(), TestData.NOW));
		assertThat(used(actor)).isEqualTo(target);
	}

	// 제출

	@Test
	void AC64_response_attachment_per_question_25mb_10MB와_15MB를_한_문항에_제출한다() throws Exception {
		JsonNode body = created(submit(respondent, new Part("q5", "a.zip", zip(10 * MB)), new Part("q5", "b.zip", zip(15 * MB))));

		assertThat(body.get("attachments")).hasSize(2);
		assertThat(body.get("attachments").get(0).get("question_id").asString()).isEqualTo("q5");
		assertThat(body.get("attachments").get(0).get("content_type").asString()).isEqualTo("application/zip");
		assertThat(storage.objectCount()).isEqualTo(2);
	}

	@Test
	void AC64_같은_문항에_20MB와_6MB는_413이고_응답과_파일이_없다() throws Exception {
		rememberCounts();

		assertError(submit(respondent, new Part("q5", "a.zip", zip(20 * MB)), new Part("q5", "b.zip", zip(6 * MB))), 413, "file_too_large");

		assertThat(storage.putCount()).isZero();
		assertNothingStored();
	}

	@Test
	void AC64_파일_하나가_25MB를_넘어도_413이다() throws Exception {
		rememberCounts();

		assertError(submit(respondent, new Part("q5", "a.zip", zip(25 * MB + 1))), 413, "file_too_large");

		assertNothingStored();
	}

	@Test
	void AC64_서로_다른_두_문항에_각각_25MB는_통과한다() throws Exception {
		JsonNode body = created(submit(respondent, new Part("q4", "a.zip", zip(25 * MB)), new Part("q5", "b.zip", zip(25 * MB))));

		assertThat(body.get("attachments")).hasSize(2);
	}

	@Test
	void 파일은_답이_없는_선택_문항에도_붙고_필수_문항을_채우지_않는다() throws Exception {
		created(submit(respondent, pdf("q5")));

		rememberCounts();
		storage.reset();
		Map<String, Object> missing = Map.of("form_version", 1, "answers", List.of(Map.of("question_id", "q1", "value", "1학년")), "consent_agreed", true);
		assertError(submit(respondent, missing, pdf("q2")), 400, "required_missing");
		assertThat(storage.putCount()).isZero();
		assertThat(attachments.count()).isEqualTo(attachmentsBefore);
	}

	@Test
	void 없는_문항의_파일은_invalid_request이고_파일_수와_ID_수가_달라도_같다() throws Exception {
		rememberCounts();

		assertError(submit(respondent, pdf("nope")), 400, "invalid_request");
		var request = multipart("/forms/" + formId + "/responses");
		request.file(data(submitBody())).file(new MockMultipartFile("files", "a.pdf", "application/pdf", PDF));
		assertError(perform(request, respondent), 400, "invalid_request");

		assertNothingStored();
	}

	@Test
	void AC64_허용_밖_형식과_제작자용_MP4는_400이고_아무것도_남지_않는다() throws Exception {
		rememberCounts();
		byte[] mp4 = "\0\0\0\u0018ftypmp42".getBytes(StandardCharsets.ISO_8859_1);

		assertError(submit(respondent, new Part("q5", "a.pdf", EXE)), 400, "invalid_file_type");
		assertError(submit(respondent, new Part("q5", "a.mp4", mp4)), 400, "invalid_file_type");

		assertThat(storage.putCount()).isZero();
		assertNothingStored();
	}

	@Test
	void AC14_동의가_없으면_400이고_초안과_파일이_그대로다() throws Exception {
		rememberCounts();
		perform(put("/me/forms/" + formId + "/response-draft").contentType(MediaType.APPLICATION_JSON)
			.content("{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"1학년\"}]}"), respondent);
		long ownerBefore = used(owner);
		long respondentBefore = used(respondent);

		assertError(submit(respondent, Map.of("form_version", 1, "answers", ANSWERS), pdf("q5")), 400, "consent_required");

		assertNothingStored();
		assertThat(used(owner)).isEqualTo(ownerBefore);
		assertThat(used(respondent)).isEqualTo(respondentBefore);
		assertThat(responses.findDraft(respondent, UUID.fromString(formId))).isPresent();
	}

	@Test
	void AC76_버전이_다르거나_폼이_닫히면_S3에_올리지_않는다() throws Exception {
		rememberCounts();
		Map<String, Object> stale = Map.of("form_version", 99, "answers", ANSWERS, "consent_agreed", true);

		assertError(submit(respondent, stale, pdf("q5")), 409, "form_version_changed");
		perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"closed\"}"), owner);
		assertError(submit(respondent, pdf("q5")), 409, "form_closed");

		assertThat(storage.putCount()).isZero();
		assertNothingStored();
	}

	@Test
	void S3가_저장한_뒤_put이_예외로_끝나도_원본을_지우고_응답이_없다() throws Exception {
		rememberCounts();
		storage.failAfterStoring();

		assertError(submit(respondent, pdf("q5")), 503, "storage_unavailable");

		assertThat(storage.deleteCount()).isEqualTo(1);
		assertNothingStored();
	}

	@Test
	void S3_저장이_중간에_실패하면_503이고_응답이_없다() throws Exception {
		rememberCounts();
		storage.failPuts();

		assertError(submit(respondent, pdf("q4"), pdf("q5")), 503, "storage_unavailable");

		assertNothingStored();
	}

	@Test
	void AC64_response_attachment_atomic_failure_DB가_실패하면_올린_원본을_모두_지운다() throws Exception {
		rememberCounts();
		doThrow(new IllegalStateException("가상 DB 실패")).when(quota).requireWithinLimit(any(), anyLong());

		assertThat(submit(respondent, pdf("q4"), pdf("q5")).getStatus()).isEqualTo(500);

		reset(quota);
		assertThat(storage.putCount()).isEqualTo(2);
		assertThat(storage.deleteCount()).isEqualTo(2);
		assertNothingStored();
	}

	// 사용량

	@Test
	void AC65_response_file_charged_to_creator_응답_파일은_제작자_사용량이고_응답자에게_세지_않는다() throws Exception {
		long ownerBefore = used(owner);
		long respondentBefore = used(respondent);

		created(submit(respondent, pdf("q5")));

		assertThat(used(owner)).isGreaterThanOrEqualTo(ownerBefore + PDF.length);
		assertThat(used(respondent)).isEqualTo(respondentBefore);
	}

	@Test
	void AC65_비로그인_파일_응답도_제작자_사용량이다() throws Exception {
		long ownerBefore = used(owner);

		created(submit(null, pdf("q5")));

		assertThat(used(owner)).isGreaterThanOrEqualTo(ownerBefore + PDF.length);
	}

	@Test
	void AC65_정확히_500MB_제출은_허용하고_1바이트_넘으면_409이고_원본을_지운다() throws Exception {
		long before = used(owner);
		String probe = created(submit(null, pdf("q5"))).get("response_id").asString();
		long cost = used(owner) - before;
		jdbc.update("delete from attachments where response_id = ?", UUID.fromString(probe));
		jdbc.update("delete from responses where response_id = ?", UUID.fromString(probe));
		fillUsageTo(owner, LIMIT - cost);

		created(submit(null, pdf("q5")));
		assertThat(used(owner)).isEqualTo(LIMIT);

		int objects = storage.objectCount();
		assertError(submit(null, pdf("q5")), 409, "storage_quota_exceeded");
		assertThat(used(owner)).isEqualTo(LIMIT);
		assertThat(storage.objectCount()).isEqualTo(objects);
	}

	@Test
	void AC65_파일_없는_제출도_한도를_넘으면_409다() throws Exception {
		fillUsageTo(owner, LIMIT);
		long count = submittedCount();

		assertError(perform(post("/forms/" + formId + "/responses").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(submitBody())), respondent), 409, "storage_quota_exceeded");

		assertThat(submittedCount()).isEqualTo(count);
		assertThat(used(owner)).isEqualTo(LIMIT);
	}

	@Test
	void AC65_초안_저장은_응답자_사용량이고_제출하면_해제되어_제작자로_옮겨간다() throws Exception {
		long ownerBefore = used(owner);
		long respondentBefore = used(respondent);
		String body = "{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"1학년\"},{\"question_id\":\"q2\",\"value\":\"홍길동\"}]}";
		MockHttpServletResponse saved = perform(put("/me/forms/" + formId + "/response-draft").contentType(MediaType.APPLICATION_JSON).content(body), respondent);
		String draftId = JSON.readTree(saved.getContentAsByteArray()).get("response_id").asString();

		assertThat(used(respondent)).isGreaterThan(respondentBefore);
		assertThat(used(owner)).isEqualTo(ownerBefore);

		created(submit(respondent, Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true, "draft_response_id", draftId), pdf("q5")));

		assertThat(used(respondent)).isEqualTo(respondentBefore);
		assertThat(used(owner)).isGreaterThan(ownerBefore + PDF.length);
	}

	@Test
	void AC65_초안_저장이_응답자_한도를_넘으면_409이고_초안이_생기지_않는다() throws Exception {
		fillUsageTo(owner, LIMIT);
		String ownForm = formId;

		assertError(perform(put("/me/forms/" + ownForm + "/response-draft").contentType(MediaType.APPLICATION_JSON)
			.content("{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"1학년\"}]}"), owner), 409, "storage_quota_exceeded");

		assertThat(responses.findDraft(owner, UUID.fromString(ownForm))).isEmpty();
		assertThat(used(owner)).isEqualTo(LIMIT);
	}

	// 다운로드

	@Test
	void AC64_response_attachment_access_제작자와_제출자만_받고_다른_응답자는_404_비로그인은_401이다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q5")));
		String path = downloadPath(body.get("response_id").asString(), body.at("/attachments/0/attachment_id").asString());

		assertThat(perform(get(path), owner).getRedirectedUrl()).contains("inline=false").contains("application%2Fpdf");
		assertThat(perform(get(path), respondent).getStatus()).isEqualTo(302);
		assertError(perform(get(path), other), 404, "not_found");
		assertThat(perform(get(path), null).getStatus()).isEqualTo(401);
	}

	@Test
	void 경로의_폼_응답과_맞지_않는_첨부는_404이고_지운_원본은_410이다() throws Exception {
		JsonNode first = created(submit(respondent, pdf("q5")));
		JsonNode second = created(submit(respondent, pdf("q5")));
		String firstResponse = first.get("response_id").asString();
		String firstFile = first.at("/attachments/0/attachment_id").asString();
		String otherForm = openForm(false, false);

		assertError(perform(get(downloadPath(second.get("response_id").asString(), firstFile)), owner), 404, "not_found");
		assertError(perform(get("/forms/" + otherForm + "/responses/" + firstResponse + "/attachments/" + firstFile), owner), 404, "not_found");

		jdbc.update("update attachments set available = false where attachment_id = ?", UUID.fromString(firstFile));
		assertError(perform(get(downloadPath(firstResponse, firstFile)), respondent), 410, "attachment_removed");
		assertError(perform(get(downloadPath(firstResponse, firstFile)), owner), 410, "attachment_removed");
		assertError(perform(get(downloadPath(firstResponse, firstFile)), other), 404, "not_found");

		jdbc.update("update forms set deleted_at = now() where form_id = ?", UUID.fromString(formId));
		assertError(perform(get(downloadPath(firstResponse, firstFile)), owner), 404, "not_found");
	}

	@Test
	void 제작자_응답_상세와_제출_이력에_첨부가_보인다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q5")));
		String responseId = body.get("response_id").asString();

		JsonNode detail = JSON.readTree(perform(get("/forms/" + formId + "/responses/" + responseId), owner).getContentAsByteArray());
		assertThat(detail.at("/attachments/0/filename").asString()).isEqualTo("제출.pdf");
		assertThat(detail.at("/attachments/0/question_id").asString()).isEqualTo("q5");
		assertThat(detail.at("/attachments/0/available").asBoolean()).isTrue();
		JsonNode history = JSON.readTree(perform(get("/me/responses/" + responseId), respondent).getContentAsByteArray());
		assertThat(history.at("/attachments/0/filename").asString()).isEqualTo("제출.pdf");
	}

	// 수정

	private Map<String, Object> editBody(List<String> keep) {
		return keep == null ? Map.of("answers", ANSWERS) : Map.of("answers", ANSWERS, "attachments", keep);
	}

	@Test
	void 수정에서_첨부를_생략하면_기존_파일을_유지한다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q5")));

		MockHttpServletResponse edited = edit(respondent, body.get("response_id").asString(), editBody(null));

		assertThat(edited.getStatus()).isEqualTo(200);
		assertThat(JSON.readTree(edited.getContentAsByteArray()).get("attachments")).hasSize(1);
		assertThat(storage.objectCount()).isEqualTo(1);
	}

	@Test
	void 수정에서_새_목록을_보내면_교체하고_끊긴_원본만_커밋_뒤에_지운다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q4"), pdf("q5")));
		String responseId = body.get("response_id").asString();
		String keep = body.at("/attachments/0/attachment_id").asString();

		MockHttpServletResponse edited = edit(respondent, responseId, editBody(List.of(keep)), new Part("q5", "새파일.zip", zip(10)));

		assertThat(edited.getStatus()).isEqualTo(200);
		JsonNode result = JSON.readTree(edited.getContentAsByteArray());
		List<String> names = new ArrayList<>();
		result.get("attachments").forEach(item -> names.add(item.get("filename").asString()));
		assertThat(names).containsExactlyInAnyOrder("제출.pdf", "새파일.zip");
		storage.awaitObjectCount(2);
		assertThat(storage.objectCount()).isEqualTo(2);
		assertThat(storage.deleteCount()).isEqualTo(1);
	}

	@Test
	void 빈_목록을_JSON으로_보내면_파일을_모두_뺀다() throws Exception {
		String responseId = created(submit(respondent, pdf("q5"))).get("response_id").asString();

		MockHttpServletResponse edited = perform(patch("/me/responses/" + responseId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(editBody(List.of()))), respondent);

		assertThat(edited.getStatus()).isEqualTo(200);
		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
	}

	@Test
	void 같은_원본을_다른_행이_쓰면_교체해도_원본을_남긴다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q5")));
		String responseId = body.get("response_id").asString();
		Attachment file = attachments.findById(UUID.fromString(body.at("/attachments/0/attachment_id").asString())).orElseThrow();
		Response another = responses.save(TestData.submitted(UUID.fromString(formId), other, 1, TestData.NOW));
		attachments.save(new Attachment(UUID.fromString(formId), another.getId(), "q5", "공유.pdf", "application/pdf", 1, file.getS3Key(), TestData.NOW));

		assertThat(edit(respondent, responseId, editBody(List.of())).getStatus()).isEqualTo(200);

		Thread.sleep(100);
		assertThat(storage.deleteCount()).isZero();
		assertThat(storage.has(file.getS3Key())).isTrue();
	}

	@Test
	void AC64_수정의_파일_검사가_실패하면_기존_응답과_첨부가_그대로다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q5")));
		String responseId = body.get("response_id").asString();
		String keep = body.at("/attachments/0/attachment_id").asString();
		int puts = storage.putCount();

		assertError(edit(respondent, responseId, Map.of("answers", List.of(Map.of("question_id", "q1", "value", "2학년"), Map.of("question_id", "q2", "value", "홍길동")), "attachments", List.of(keep)),
				new Part("q5", "a.pdf", EXE)), 400, "invalid_file_type");

		assertThat(storage.putCount()).isEqualTo(puts);
		assertThat(storage.objectCount()).isEqualTo(1);
		JsonNode history = JSON.readTree(perform(get("/me/responses/" + responseId), respondent).getContentAsByteArray());
		assertThat(history.at("/content/0/value").asString()).isEqualTo("1학년");
		assertThat(history.get("attachments")).hasSize(1);
	}

	@Test
	void 수정의_25MB는_남긴_파일과_새_파일을_합쳐_판정한다() throws Exception {
		JsonNode body = created(submit(respondent, new Part("q5", "a.zip", zip(20 * MB))));
		String keep = body.at("/attachments/0/attachment_id").asString();
		String responseId = body.get("response_id").asString();

		assertError(edit(respondent, responseId, editBody(List.of(keep)), new Part("q5", "b.zip", zip(6 * MB))), 413, "file_too_large");
		assertThat(edit(respondent, responseId, editBody(List.of()), new Part("q5", "b.zip", zip(6 * MB))).getStatus()).isEqualTo(200);
	}

	@Test
	void 수정의_DB가_실패하면_S3_삭제를_부르지_않고_새_원본만_지운다() throws Exception {
		JsonNode body = created(submit(respondent, pdf("q5")));
		String responseId = body.get("response_id").asString();
		doThrow(new IllegalStateException("가상 DB 실패")).when(quota).requireWithinLimit(any(), anyLong());

		assertThat(edit(respondent, responseId, editBody(List.of()), pdf("q5")).getStatus()).isEqualTo(500);

		reset(quota);
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(storage.deleteCount()).isEqualTo(1);
		JsonNode history = JSON.readTree(perform(get("/me/responses/" + responseId), respondent).getContentAsByteArray());
		assertThat(history.at("/attachments/0/attachment_id").asString()).isEqualTo(body.at("/attachments/0/attachment_id").asString());
	}

	@Test
	void 수정_권한이_없으면_S3에_올리지_않는다() throws Exception {
		formId = openForm(false, false);
		String responseId = created(submit(respondent, pdf("q5"))).get("response_id").asString();
		int puts = storage.putCount();

		assertError(edit(respondent, responseId, editBody(null), pdf("q5")), 409, "not_editable");
		assertError(edit(other, responseId, editBody(null), pdf("q5")), 404, "not_found");

		assertThat(storage.putCount()).isEqualTo(puts);
	}

	@Test
	void AC65_수정이_제작자_한도를_넘으면_409이고_그대로다() throws Exception {
		String responseId = created(submit(respondent)).get("response_id").asString();
		fillUsageTo(owner, LIMIT);

		assertError(edit(respondent, responseId, editBody(null), pdf("q5")), 409, "storage_quota_exceeded");

		assertThat(used(owner)).isEqualTo(LIMIT);
		assertThat(storage.objectCount()).isZero();
	}

	// 취소

	@Test
	void 취소한_응답의_파일은_받을_수_없고_어느_사용량에도_없고_원본이_지워진다() throws Exception {
		long ownerBefore = used(owner);
		long respondentBefore = used(respondent);
		JsonNode body = created(submit(respondent, pdf("q5")));
		String responseId = body.get("response_id").asString();
		String path = downloadPath(responseId, body.at("/attachments/0/attachment_id").asString());

		assertThat(perform(delete("/me/responses/" + responseId), respondent).getStatus()).isEqualTo(204);

		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
		assertError(perform(get(path), owner), 404, "not_found");
		assertThat(used(owner)).isEqualTo(ownerBefore);
		assertThat(used(respondent)).isEqualTo(respondentBefore);
	}

}
