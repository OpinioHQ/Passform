package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.MemoryFileStorage;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 문항별 응답 파일 첨부 켜기/끄기(allow_response_files, SPEC 5절 응답 파일 규칙, AC64)를 실제 Postgres로 시험한다.
// 3문항 중 q2만 켠 폼에서 켠 문항만 파일을 받고, 꺼진 문항의 파일은 S3 업로드 전에 400 file_upload_not_allowed로 거절한다.
@Import(MemoryFileStorage.Config.class)
class ResponseFileToggleApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final byte[] PDF = "%PDF-1.7 가상 문서".getBytes(StandardCharsets.UTF_8);

	private static final List<Map<String, Object>> ANSWERS = List.of(Map.of("question_id", "q1", "value", "가상 답"));

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

	private MockMvc mockMvc;

	private UUID owner;

	private UUID respondent;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		storage.reset();
		owner = users.save(TestData.newUser()).getId();
		respondent = users.save(TestData.newUser()).getId();
	}

	@AfterEach
	void removeCommittedRows() {
		jdbc.update("delete from attachments");
		jdbc.update("delete from responses");
	}

	// q1 단답, q2 장문, q3 체크박스. onlyQ2가 true면 q2만 켜고, false면 어느 문항에도 필드가 없다(기존 폼).
	private static Map<String, Object> threeQuestions(boolean onlyQ2) {
		String q2Flag = onlyQ2 ? "\"allow_response_files\": true, " : "";
		return SampleForms.parse("""
				{
				  "title": "가상 첨부 폼", "note": "", "deadline": null,
				  "settings": {"editable": true, "cancellable": false, "response_limit": null,
				    "display": {"default_mode": "one_question"}},
				  "payment_link": null, "consent_template_id": "consent_default", "note_attachments": [],
				  "sections": [
				    {"section_id": "s1", "title": "기본", "questions": [
				      {"question_id": "q1", "template_id": null, "question_note": "한 줄 소개", "question_type": "short_answer",
				       "options": [], "required": false, "profile_key": null, "attachments": [], "branch_rules": []},
				      {"question_id": "q2", "template_id": null, "question_note": "포트폴리오", "question_type": "long_answer",
				       "options": [], "required": false, "profile_key": null, "attachments": [], %s"branch_rules": []},
				      {"question_id": "q3", "template_id": null, "question_note": "관심 분야", "question_type": "checkbox",
				       "options": ["가", "나"], "required": false, "profile_key": null, "attachments": [], "branch_rules": []}
				    ]}
				  ]
				}
				""".formatted(q2Flag));
	}

	private String openForm(Map<String, Object> definition) throws Exception {
		MockHttpServletResponse created = perform(post("/forms").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(definition)), owner);
		String id = JSON.readTree(created.getContentAsByteArray()).get("form_id").asString();
		assertThat(perform(patch("/forms/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"open\"}"),
				owner).getStatus()).isEqualTo(200);
		return id;
	}

	private MockHttpServletResponse perform(AbstractMockHttpServletRequestBuilder<?> request, UUID actor) throws Exception {
		if (actor != null) {
			request.with(user(actor.toString()));
		}
		return mockMvc.perform(request.with(csrf())).andReturn().getResponse();
	}

	private AbstractMockHttpServletRequestBuilder<?> withFiles(MockMultipartHttpServletRequestBuilder request,
			Map<String, Object> body, String... fileQuestionIds) {
		request.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(body)));
		for (String questionId : fileQuestionIds) {
			request.file(new MockMultipartFile("files", "제출.pdf", "text/plain", PDF));
			request.param("file_question_ids", questionId);
		}
		return request;
	}

	private MockHttpServletResponse submit(String formId, int version, String... fileQuestionIds) throws Exception {
		Map<String, Object> body = Map.of("form_version", version, "answers", ANSWERS, "consent_agreed", true);
		return perform(withFiles(multipart("/forms/" + formId + "/responses"), body, fileQuestionIds), respondent);
	}

	private MockHttpServletResponse edit(String responseId, Map<String, Object> body, String... fileQuestionIds)
			throws Exception {
		return perform(withFiles(multipart(HttpMethod.PATCH, "/me/responses/" + responseId), body, fileQuestionIds),
				respondent);
	}

	private JsonNode created(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isEqualTo(201);
		return JSON.readTree(response.getContentAsByteArray());
	}

	private static void assertError(MockHttpServletResponse response, int status, String code) throws Exception {
		assertThat(response.getStatus()).isEqualTo(status);
		assertThat(JSON.readTree(response.getContentAsByteArray()).get("error").asString()).isEqualTo(code);
	}

	private long used(UUID actor) throws Exception {
		return JSON.readTree(perform(get("/me/storage"), actor).getContentAsByteArray()).get("used_bytes").asLong();
	}

	private long submittedCount(String formId) {
		return responses.countSubmittedNotDeleted(UUID.fromString(formId));
	}

	@Test
	void 켠_문항_q2의_파일_제출은_성공하고_그_문항에_연결된다() throws Exception {
		String formId = openForm(threeQuestions(true));

		JsonNode body = created(submit(formId, 1, "q2"));

		assertThat(body.get("attachments")).hasSize(1);
		assertThat(body.at("/attachments/0/question_id").asString()).isEqualTo("q2");
		assertThat(storage.objectCount()).isEqualTo(1);
	}

	@Test
	void 꺼진_문항_q1의_파일이_섞인_제출은_400이고_응답_파일_사용량이_변하지_않으며_S3에_올리지_않는다() throws Exception {
		String formId = openForm(threeQuestions(true));
		long usedBefore = used(owner);
		long attachmentsBefore = attachments.count();

		assertError(submit(formId, 1, "q2", "q1"), 400, "file_upload_not_allowed");
		assertError(submit(formId, 1, "q3"), 400, "file_upload_not_allowed");

		assertThat(submittedCount(formId)).isZero();
		assertThat(attachments.count()).isEqualTo(attachmentsBefore);
		assertThat(storage.putCount()).isZero();
		assertThat(storage.objectCount()).isZero();
		assertThat(used(owner)).isEqualTo(usedBefore);
	}

	@Test
	void 꺼진_문항에_새_파일을_보내는_수정은_400이고_기존_응답과_파일을_바꾸지_않는다() throws Exception {
		String formId = openForm(threeQuestions(true));
		JsonNode body = created(submit(formId, 1, "q2"));
		String responseId = body.get("response_id").asString();
		int putsBefore = storage.putCount();
		long usedBefore = used(owner);

		Map<String, Object> changed = Map.of("answers", List.of(Map.of("question_id", "q1", "value", "바꾼 답")));
		assertError(edit(responseId, changed, "q2", "q1"), 400, "file_upload_not_allowed");

		assertThat(storage.putCount()).isEqualTo(putsBefore);
		assertThat(storage.objectCount()).isEqualTo(1);
		assertThat(used(owner)).isEqualTo(usedBefore);
		assertThat(attachments.findByResponseIdOrderByCreatedAtAscIdAsc(UUID.fromString(responseId))).hasSize(1);
		assertThat(responses.findById(UUID.fromString(responseId)).orElseThrow().getUpdatedAt()).isNull();
	}

	@Test
	void 필드가_없는_기존_폼은_파일_없이_정상_제출되고_파일은_거절된다() throws Exception {
		String formId = openForm(threeQuestions(false));

		MockHttpServletResponse plain = perform(post("/forms/" + formId + "/responses").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true))),
				respondent);
		assertThat(plain.getStatus()).isEqualTo(201);

		assertError(submit(formId, 1, "q2"), 400, "file_upload_not_allowed");
		assertThat(storage.putCount()).isZero();
	}

	@Test
	void 첨부가_꺼진_문항에_이미_있는_파일은_수정_때_유지하거나_뺄_수_있다() throws Exception {
		String formId = openForm(threeQuestions(false));
		MockHttpServletResponse plain = perform(post("/forms/" + formId + "/responses").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("form_version", 1, "answers", ANSWERS, "consent_agreed", true))),
				respondent);
		UUID responseId = UUID.fromString(JSON.readTree(plain.getContentAsByteArray()).get("response_id").asString());
		// 기능이 생기기 전에 제출된 파일과 같은 상태다.
		Attachment old = attachments.save(new Attachment(UUID.fromString(formId), responseId, "q1", "예전.pdf",
				"application/pdf", PDF.length, "legacy/" + UUID.randomUUID(), TestData.NOW));

		Map<String, Object> keepAll = Map.of("answers", List.of(Map.of("question_id", "q1", "value", "바꾼 답")));
		MockHttpServletResponse kept = edit(responseId.toString(), keepAll);
		assertThat(kept.getStatus()).isEqualTo(200);
		JsonNode keptBody = JSON.readTree(kept.getContentAsByteArray());
		assertThat(keptBody.get("attachments")).hasSize(1);
		assertThat(keptBody.at("/attachments/0/attachment_id").asString()).isEqualTo(old.getId().toString());

		Map<String, Object> removeAll = Map.of("answers", ANSWERS, "attachments", List.of());
		MockHttpServletResponse removed = edit(responseId.toString(), removeAll);
		assertThat(removed.getStatus()).isEqualTo(200);
		assertThat(JSON.readTree(removed.getContentAsByteArray()).get("attachments")).isEmpty();
	}

	@Test
	void 배포한_폼에서_값을_바꾸면_새_버전이고_수정은_제출한_버전의_정의로_판정한다() throws Exception {
		String formId = openForm(threeQuestions(true));
		String responseId = created(submit(formId, 1)).get("response_id").asString();

		Map<String, Object> next = threeQuestions(true);
		SampleForms.question(next, 0, 0).put("allow_response_files", true);
		MockHttpServletResponse patched = perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("sections", next.get("sections")))), owner);
		assertThat(patched.getStatus()).isEqualTo(200);
		assertThat(JSON.readTree(patched.getContentAsByteArray()).get("definition_version").asInt()).isEqualTo(2);

		// 버전 2에서는 q1 파일을 받는다.
		assertThat(created(submit(formId, 2, "q1")).at("/attachments/0/question_id").asString()).isEqualTo("q1");
		// 버전 1로 제출한 응답의 수정은 버전 1 정의(q1 꺼짐)로 판정한다.
		assertError(edit(responseId, Map.of("answers", ANSWERS), "q1"), 400, "file_upload_not_allowed");
	}

	@Test
	void 폼을_복사해도_켠_설정이_유지되고_공개_조회에도_보인다() throws Exception {
		String formId = openForm(threeQuestions(true));

		MockHttpServletResponse copied = perform(post("/forms/" + formId + "/copy"), owner);
		assertThat(copied.getStatus()).isEqualTo(201);
		String copyId = JSON.readTree(copied.getContentAsByteArray()).get("form_id").asString();
		JsonNode definition = JSON.readTree(perform(get("/forms/" + copyId + "/definition"), owner).getContentAsByteArray());
		JsonNode questions = definition.at("/sections/0/questions");
		assertThat(questions.get(1).get("allow_response_files").asBoolean()).isTrue();
		assertThat(questions.get(0).has("allow_response_files")).isFalse();

		JsonNode publicForm = JSON.readTree(perform(get("/forms/" + formId), null).getContentAsByteArray());
		assertThat(publicForm.at("/sections/0/questions/1/allow_response_files").asBoolean()).isTrue();
	}

	@Test
	void false를_명시한_배포_폼에서_키를_빼고_저장해도_새_버전을_만들지_않는다() throws Exception {
		Map<String, Object> explicitFalse = threeQuestions(true);
		SampleForms.question(explicitFalse, 0, 0).put("allow_response_files", false);
		String formId = openForm(explicitFalse);

		// 화면에서 켰다가 다시 끄면 키가 빠진 정의를 보낸다.
		MockHttpServletResponse withoutKey = perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("sections", threeQuestions(true).get("sections")))), owner);
		assertThat(JSON.readTree(withoutKey.getContentAsByteArray()).get("definition_version").asInt()).isEqualTo(1);

		// 반대로 키가 없던 문항에 false를 보내도 같다.
		MockHttpServletResponse backToFalse = perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("sections", explicitFalse.get("sections")))), owner);
		assertThat(JSON.readTree(backToFalse.getContentAsByteArray()).get("definition_version").asInt()).isEqualTo(1);
		assertThat(created(submit(formId, 1, "q2")).get("attachments")).hasSize(1);
	}

	@Test
	void 값이_같은_정의를_다시_저장하면_새_버전을_만들지_않는다() throws Exception {
		String formId = openForm(threeQuestions(true));

		MockHttpServletResponse patched = perform(patch("/forms/" + formId).contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("sections", threeQuestions(true).get("sections")))), owner);

		assertThat(JSON.readTree(patched.getContentAsByteArray()).get("definition_version").asInt()).isEqualTo(1);
	}

}
