package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormRepository;
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

// 받은 응답 전체 삭제, 응답 첨부 전체 삭제, 폼 삭제(SPEC 5절 119·237·238행, 224행, AC2, AC16, AC17, AC19, AC66, AC67, AC70, AC83).
// 커밋 뒤에만 일어나는 원본 삭제를 봐야 해서 트랜잭션으로 묶지 않는다. 파일은 메모리 저장소다.
@Import(MemoryFileStorage.Config.class)
class FormDeletionApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final byte[] PDF = "%PDF-1.7 가상 문서".getBytes(StandardCharsets.UTF_8);

	private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

	private static final List<Map<String, Object>> ANSWERS = List.of(Map.of("question_id", "q1", "value", "1학년"),
			Map.of("question_id", "q2", "value", "홍길동"));

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MemoryFileStorage storage;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository versions;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private AttachmentRepository attachments;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private StorageQuotaService quota;

	private MockMvc mockMvc;

	private UUID owner;

	private UUID respondent;

	private UUID other;

	private String formId;

	private String slug;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		storage.reset();
		owner = users.save(TestData.newUser()).getId();
		respondent = users.save(TestData.newUser()).getId();
		other = users.save(TestData.newUser()).getId();
		Map<String, Object> definition = SampleForms.unbranched();
		@SuppressWarnings("unchecked")
		Map<String, Object> settings = (Map<String, Object>) definition.get("settings");
		settings.put("editable", true);
		settings.put("cancellable", true);
		MockHttpServletResponse created = perform(post("/forms").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(definition)), owner);
		formId = JSON.readTree(created.getContentAsByteArray()).get("form_id").asString();
		perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"open\"}"), owner);
		slug = forms.findById(UUID.fromString(formId)).orElseThrow().getSlug();
	}

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

	private JsonNode json(MockHttpServletResponse response) throws Exception {
		return JSON.readTree(response.getContentAsByteArray());
	}

	private JsonNode submit(UUID actor) throws Exception {
		var request = MockMvcRequestBuilders.multipart("/forms/" + formId + "/responses");
		request.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
				JSON.writeValueAsBytes(Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true))));
		request.file(new MockMultipartFile("files", "제출.pdf", "application/pdf", PDF));
		request.param("file_question_ids", "q5");
		MockHttpServletResponse response = perform(request, actor);
		assertThat(response.getStatus()).isEqualTo(201);
		return json(response);
	}

	private MockHttpServletResponse deleteResponses(UUID actor) throws Exception {
		return perform(delete("/forms/" + formId + "/responses"), actor);
	}

	private MockHttpServletResponse deleteFiles(UUID actor) throws Exception {
		return perform(delete("/forms/" + formId + "/response-attachments"), actor);
	}

	private MockHttpServletResponse deleteForm(UUID actor) throws Exception {
		return perform(delete("/forms/" + formId), actor);
	}

	private long used(UUID actor) throws Exception {
		return json(perform(get("/me/storage"), actor)).get("used_bytes").asLong();
	}

	private String filePath(JsonNode submitted) {
		return "/forms/" + formId + "/responses/" + submitted.get("response_id").asString() + "/attachments/"
				+ submitted.at("/attachments/0/attachment_id").asString();
	}

	private long responseCount() throws Exception {
		return json(perform(get("/forms/" + formId + "/results"), owner)).get("response_count").asLong();
	}

	private static void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
		assertThat(response.getStatus()).isEqualTo(status);
		assertThat(JSON.readTree(response.getContentAsByteArray()).get("error").asString()).isEqualTo(code);
	}

	private void assertResponsesIntact(int count, int objects) throws Exception {
		assertThat(responseCount()).isEqualTo(count);
		Thread.sleep(100);
		assertThat(storage.deleteCount()).isZero();
		assertThat(storage.objectCount()).isEqualTo(objects);
	}

	private void failInsideTransaction() {
		// 삭제 예약까지 실제로 한 뒤 실패시킨다. 커밋 전에 원본을 지우는 코드가 있으면 여기서 드러난다.
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("가상 DB 실패");
		}).when(quota).releaseKeys(any(), anyCollection());
	}

	// 받은 응답 전체 삭제

	@Test
	void AC66_creator_delete_all_responses_결과에서_빠지고_원본이_지워지며_폼은_유지된다() throws Exception {
		submit(respondent);
		submit(other);
		long before = used(owner);

		assertThat(deleteResponses(owner).getStatus()).isEqualTo(204);

		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
		assertThat(responseCount()).isZero();
		assertThat(used(owner)).isLessThan(before);
		assertThat(perform(get("/f/" + slug), null).getStatus()).isEqualTo(200);
		assertThat(perform(get("/forms/" + formId + "/definition"), owner).getStatus()).isEqualTo(200);
	}

	@Test
	void AC66_제작자_상세는_404이고_내보내기_행이_없다() throws Exception {
		String responseId = submit(respondent).get("response_id").asString();

		deleteResponses(owner);

		assertThat(perform(get("/forms/" + formId + "/responses/" + responseId), owner).getStatus()).isEqualTo(404);
		MockHttpServletResponse export = perform(post("/forms/" + formId + "/export").contentType(MediaType.APPLICATION_JSON).content("{\"format\":\"json\"}"), owner);
		assertThat(export.getStatus()).isEqualTo(200);
		assertThat(json(export).get("responses")).isEmpty();
	}

	@Test
	void AC17_bulk_delete_keeps_submitted_history_이력은_문구와_답이_남고_첨부는_삭제로_표시된다() throws Exception {
		JsonNode submitted = submit(respondent);
		String responseId = submitted.get("response_id").asString();

		deleteResponses(owner);

		JsonNode history = json(perform(get("/me/responses/" + responseId), respondent));
		assertThat(history.get("form_title").asString()).isEqualTo("가상 신청");
		assertThat(history.get("form_definition").isNull()).isTrue();
		assertThat(history.at("/content/0/question_note").asString()).isEqualTo("학년");
		assertThat(history.at("/content/0/value").asString()).isEqualTo("1학년");
		assertThat(history.at("/attachments/0/available").asBoolean()).isFalse();
		assertThat(history.get("editable").asBoolean()).isFalse();
		assertThat(history.get("cancellable").asBoolean()).isFalse();
		assertError(perform(get(filePath(submitted)), respondent), 410, "attachment_removed");
		JsonNode inbox = json(perform(get("/me/forms"), respondent));
		assertThat(inbox.get(0).get("state").asString()).isEqualTo("submitted");
	}

	@Test
	void AC16_이력만_남은_응답의_수정과_취소는_history_only다() throws Exception {
		String responseId = submit(respondent).get("response_id").asString();

		deleteResponses(owner);

		assertError(perform(patch("/me/responses/" + responseId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("answers", ANSWERS))), respondent), 409, "history_only");
		assertError(perform(delete("/me/responses/" + responseId), respondent), 409, "history_only");
	}

	@Test
	void 비로그인_응답과_첨부_행은_FK_오류_없이_지워지고_이력으로_남지_않는다() throws Exception {
		String responseId = submit(null).get("response_id").asString();

		assertThat(deleteResponses(owner).getStatus()).isEqualTo(204);

		assertThat(responses.findById(UUID.fromString(responseId))).isEmpty();
		assertThat(attachments.count()).isZero();
	}

	@Test
	void AC83_작성_중_응답은_받은_응답_삭제_대상이_아니다() throws Exception {
		perform(put("/me/forms/" + formId + "/response-draft").contentType(MediaType.APPLICATION_JSON)
			.content("{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"1학년\"}]}"), respondent);

		deleteResponses(owner);

		assertThat(responses.findDraft(respondent, UUID.fromString(formId))).isPresent();
	}

	@Test
	void AC66_폼이_열려_있으면_삭제_뒤_새_응답을_제출할_수_있다() throws Exception {
		submit(respondent);
		deleteResponses(owner);

		submit(respondent);

		assertThat(responseCount()).isEqualTo(1);
	}

	@Test
	void 응답_전체_삭제의_DB가_실패하면_S3_삭제_0회이고_모두_그대로다() throws Exception {
		submit(respondent);
		submit(null);
		failInsideTransaction();

		assertThat(deleteResponses(owner).getStatus()).isEqualTo(500);

		reset(quota);
		assertResponsesIntact(2, 2);
	}

	@Test
	void AC2_제작자가_아니면_403이고_설정과_무관하게_제작자는_삭제한다() throws Exception {
		submit(respondent);

		assertError(deleteResponses(respondent), 403, "forbidden");
		assertError(deleteFiles(other), 403, "forbidden");
		assertError(deleteForm(other), 403, "forbidden");
		assertThat(deleteResponses(null).getStatus()).isEqualTo(401);
		assertResponsesIntact(1, 1);

		perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"closed\"}"), owner);
		assertThat(deleteResponses(owner).getStatus()).isEqualTo(204);
	}

	@Test
	void 같은_폼의_제출과_전체_삭제가_겹쳐도_일부만_삭제되지_않는다() throws Exception {
		List<UUID> respondents = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			UUID user = users.save(TestData.newUser()).getId();
			respondents.add(user);
			submit(user);
		}
		String body = JSON.writeValueAsString(Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true));
		List<Callable<Integer>> calls = new ArrayList<>();
		calls.add(() -> deleteResponses(owner).getStatus());
		for (int i = 0; i < 3; i++) {
			calls.add(() -> perform(post("/forms/" + formId + "/responses").contentType(MediaType.APPLICATION_JSON).content(body), other).getStatus());
		}
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		List<Future<Integer>> results = pool.invokeAll(calls);
		pool.shutdown();

		for (Future<Integer> result : results) {
			assertThat(result.get()).isIn(201, 204);
		}
		// 삭제가 본 시점의 응답은 전부 지워지고, 삭제 뒤에 들어온 제출만 남는다.
		for (UUID user : respondents) {
			assertThat(jdbc.queryForObject("select count(*) from responses where user_id = ? and creator_deleted_at is null", Long.class, user)).isZero();
		}
		assertThat(responseCount()).isBetween(0L, 3L);
		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
	}

	// 응답 첨부 전체 삭제

	@Test
	void AC70_delete_all_response_attachments_파일만_지우고_답과_응답_수는_유지하며_사용량이_준다() throws Exception {
		JsonNode first = submit(respondent);
		JsonNode second = submit(other);
		long before = used(owner);

		assertThat(deleteFiles(owner).getStatus()).isEqualTo(204);

		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
		assertThat(responseCount()).isEqualTo(2);
		assertThat(used(owner)).isEqualTo(before - 2L * PDF.length);
		assertError(perform(get(filePath(first)), owner), 410, "attachment_removed");
		assertError(perform(get(filePath(second)), other), 410, "attachment_removed");
		JsonNode detail = json(perform(get("/forms/" + formId + "/responses/" + first.get("response_id").asString()), owner));
		assertThat(detail.at("/content/0/value").asString()).isEqualTo("1학년");
		assertThat(detail.at("/attachments/0/available").asBoolean()).isFalse();
	}

	@Test
	void 응답_첨부_전체_삭제의_DB가_실패하면_일부만_지워지지_않는다() throws Exception {
		JsonNode first = submit(respondent);
		submit(other);
		failInsideTransaction();

		assertThat(deleteFiles(owner).getStatus()).isEqualTo(500);

		reset(quota);
		assertResponsesIntact(2, 2);
		assertThat(perform(get(filePath(first)), respondent).getStatus()).isEqualTo(302);
	}

	@Test
	void AC70_특정_응답만_지우는_API는_없다() throws Exception {
		String responseId = submit(respondent).get("response_id").asString();

		assertThat(perform(delete("/forms/" + formId + "/responses/" + responseId), owner).getStatus()).isIn(404, 405);
		assertThat(perform(delete("/forms/" + formId + "/responses/" + responseId + "/attachments"), owner).getStatus()).isIn(404, 405);
		assertThat(storage.objectCount()).isEqualTo(1);
	}

	// 폼 삭제

	private String attachCreatorFile() throws Exception {
		var upload = MockMvcRequestBuilders.multipart("/forms/" + formId + "/attachments");
		upload.file(new MockMultipartFile("file", "a.png", "image/png", PNG));
		return json(perform(upload, owner)).get("attachment_id").asString();
	}

	@Test
	void AC67_delete_form_정의와_버전을_지우고_forms_행은_남기며_공유_링크와_재제출은_404다() throws Exception {
		submit(respondent);
		String creatorFile = attachCreatorFile();

		assertThat(deleteForm(owner).getStatus()).isEqualTo(204);

		storage.awaitObjectCount(0);
		assertThat(storage.objectCount()).isZero();
		UUID id = UUID.fromString(formId);
		assertThat(forms.findById(id)).isPresent();
		assertThat(forms.findById(id).orElseThrow().getDeletedAt()).isNotNull();
		assertThat(forms.findById(id).orElseThrow().getWorkingDefinition()).isNull();
		assertThat(versions.findByFormId(id)).isEmpty();
		assertThat(perform(get("/f/" + slug), null).getStatus()).isEqualTo(404);
		assertThat(perform(get("/forms/" + formId), null).getStatus()).isEqualTo(404);
		assertThat(perform(get("/forms/" + formId + "/attachments/" + creatorFile), owner).getStatus()).isEqualTo(404);
		assertThat(perform(post("/forms/" + formId + "/responses").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true))), respondent).getStatus()).isEqualTo(404);
	}

	@Test
	void AC67_제작자_목록_결과_분석_내보내기에서_빠지고_사용량이_0이_된다() throws Exception {
		submit(respondent);

		deleteForm(owner);

		assertThat(json(perform(get("/me/created-forms"), owner))).isEmpty();
		assertThat(perform(get("/forms/" + formId + "/results"), owner).getStatus()).isEqualTo(404);
		assertThat(perform(post("/forms/" + formId + "/analyze").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"전체 몇 명\"}"), owner).getStatus()).isEqualTo(404);
		assertThat(perform(post("/forms/" + formId + "/export").contentType(MediaType.APPLICATION_JSON).content("{\"format\":\"json\"}"), owner).getStatus()).isEqualTo(404);
		assertThat(used(owner)).isZero();
	}

	@Test
	void AC67_delete_form_preserves_submitter_history_제출_버전_문구가_남고_수정_취소는_history_only다() throws Exception {
		JsonNode submitted = submit(respondent);
		String responseId = submitted.get("response_id").asString();

		deleteForm(owner);

		JsonNode history = json(perform(get("/me/responses/" + responseId), respondent));
		assertThat(history.get("form_title").asString()).isEqualTo("가상 신청");
		assertThat(history.get("form_definition").isNull()).isTrue();
		assertThat(history.at("/content/0/question_note").asString()).isEqualTo("학년");
		assertThat(history.at("/attachments/0/available").asBoolean()).isFalse();
		assertThat(json(perform(get("/me/responses"), respondent)).get(0).get("form_title").asString()).isEqualTo("가상 신청");
		assertError(perform(delete("/me/responses/" + responseId), respondent), 409, "history_only");
		assertThat(perform(get(filePath(submitted)), respondent).getStatus()).isEqualTo(404);
	}

	@Test
	void AC83_폼_삭제는_초안과_내_폼함_항목을_지운다() throws Exception {
		perform(put("/me/forms/" + formId + "/response-draft").contentType(MediaType.APPLICATION_JSON)
			.content("{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"1학년\"}]}"), respondent);
		assertThat(json(perform(get("/me/forms"), respondent))).hasSize(1);
		long respondentUsage = used(respondent);
		assertThat(respondentUsage).isPositive();

		deleteForm(owner);

		assertThat(responses.findDraft(respondent, UUID.fromString(formId))).isEmpty();
		assertThat(json(perform(get("/me/forms"), respondent))).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from saved_forms where form_id = ?", Long.class, UUID.fromString(formId))).isZero();
		assertThat(used(respondent)).isZero();
	}

	@Test
	void 받은_응답을_지운_뒤_폼을_삭제해도_이력이_유지된다() throws Exception {
		String responseId = submit(respondent).get("response_id").asString();
		deleteResponses(owner);

		assertThat(deleteForm(owner).getStatus()).isEqualTo(204);

		JsonNode history = json(perform(get("/me/responses/" + responseId), respondent));
		assertThat(history.get("form_title").asString()).isEqualTo("가상 신청");
		assertThat(history.at("/content/0/question_note").asString()).isEqualTo("학년");
	}

	@Test
	void 폼_삭제의_DB가_실패하면_S3_삭제_0회이고_모든_행이_그대로다() throws Exception {
		submit(respondent);
		attachCreatorFile();
		failInsideTransaction();

		assertThat(deleteForm(owner).getStatus()).isEqualTo(500);

		reset(quota);
		assertResponsesIntact(1, 2);
		assertThat(versions.findByFormId(UUID.fromString(formId))).isNotEmpty();
		assertThat(perform(get("/f/" + slug), null).getStatus()).isEqualTo(200);
	}

	@Test
	void 이미_삭제한_폼을_다시_지우면_404다() throws Exception {
		deleteForm(owner);

		assertThat(deleteForm(owner).getStatus()).isEqualTo(404);
	}

}
