package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

// 제작자 도구 API(E14 #59): 분기 그래프(AC45), QR(AC10), 폼 복사(AC40, AC90). 권한은 AC2.
class CreatorToolsApiDatabaseTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID owner;

	private UUID other;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		owner = users.saveAndFlush(TestData.newUser()).getId();
		other = users.saveAndFlush(TestData.newUser()).getId();
	}

	// 전체 경로를 시험하느라 커밋하므로 직접 지운다.
	@AfterEach
	void removeCommittedRows() {
		Object[] userIds = { owner, other };
		String ownedForms = "(select form_id from forms where owner_id in (?, ?))";
		jdbc.update("delete from responses where form_id in " + ownedForms, userIds);
		jdbc.update("delete from form_versions where form_id in " + ownedForms, userIds);
		jdbc.update("delete from forms where owner_id in (?, ?)", userIds);
		jdbc.update("delete from users where user_id in (?, ?)", userIds);
	}

	private ResultActions patchForm(UUID user, String formId, Object body) throws Exception {
		return mockMvc.perform(patch("/forms/" + formId).with(user(user.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body)));
	}

	private String createDraft(Map<String, Object> definition) throws Exception {
		String body = mockMvc
			.perform(post("/forms").with(user(owner.toString()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(definition)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JSON.readTree(body).get("form_id").asString();
	}

	private void expectForbiddenWithoutContent(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden())
			.andExpect(content().json("{\"error\":\"forbidden\"}", JsonCompareMode.STRICT));
	}

	private ResultActions branchGraph(UUID user, String formId) throws Exception {
		return mockMvc.perform(get("/forms/" + formId + "/branch-graph").with(user(user.toString())));
	}

	@SuppressWarnings("unchecked")
	private static List<Object> sections(Map<String, Object> definition) {
		return (List<Object>) definition.get("sections");
	}

	@SuppressWarnings("unchecked")
	private static List<Object> rules(Map<String, Object> question) {
		return (List<Object>) question.get("branch_rules");
	}

	private static Map<String, Object> rule(String questionId, String option, Map<String, Object> goTo) {
		return Map.of("when", Map.of("match", "all", "conditions", List.of(Map.of("question_id", questionId, "option", option))),
				"go_to", goTo);
	}

	// branched()에 규칙 2개와 빈 섹션을 더한다: q2의 둘째 규칙은 문항 q4로, q3의 규칙은 제출로 간다. s2와 s3 사이에 빈 섹션이 있다.
	private static Map<String, Object> graphForm() {
		Map<String, Object> definition = SampleForms.branched();
		rules(SampleForms.question(definition, 0, 1)).add(rule("q2", "예", Map.of("question_id", "q4")));
		Map<String, Object> q3 = SampleForms.question(definition, 1, 0);
		q3.put("question_type", "multiple_choice");
		q3.put("options", List.of("리더", "멤버"));
		rules(q3).add(rule("q3", "리더", Map.of("submit", true)));
		List<Object> sections = new ArrayList<>(sections(definition));
		Map<String, Object> empty = new LinkedHashMap<>();
		empty.put("section_id", "s_empty");
		empty.put("title", "빈 섹션");
		empty.put("questions", new ArrayList<>());
		sections.add(2, empty);
		definition.put("sections", sections);
		return definition;
	}

	@Test
	void branch_graph_matches_rules() throws Exception {
		String formId = createDraft(graphForm());

		branchGraph(owner, formId).andExpect(status().isOk()).andExpect(content().json("""
				{"nodes": [
				  {"id": "s1", "type": "section"}, {"id": "q1", "type": "question"}, {"id": "q2", "type": "question"},
				  {"id": "s2", "type": "section"}, {"id": "q3", "type": "question"},
				  {"id": "s_empty", "type": "section"},
				  {"id": "s3", "type": "section"}, {"id": "q4", "type": "question"},
				  {"id": "submit", "type": "submit"}],
				 "edges": [
				  {"from": "q1", "to": "q2", "kind": "default"},
				  {"from": "q2", "to": "s3", "kind": "rule", "rule_index": 0},
				  {"from": "q2", "to": "q4", "kind": "rule", "rule_index": 1},
				  {"from": "q2", "to": "q3", "kind": "default"},
				  {"from": "q3", "to": "submit", "kind": "rule", "rule_index": 0},
				  {"from": "q3", "to": "q4", "kind": "default"},
				  {"from": "q4", "to": "submit", "kind": "default"}]}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void branch_graph_matches_rules_저장된_초안_기준이고_분기가_없어도_기본_이동은_모두_있다() throws Exception {
		String formId = createDraft(SampleForms.branched());
		patchForm(owner, formId, Map.of("sections", sections(SampleForms.unbranched()))).andExpect(status().isOk());

		branchGraph(owner, formId).andExpect(status().isOk()).andExpect(content().json("""
				{"nodes": [
				  {"id": "s1", "type": "section"}, {"id": "q1", "type": "question"}, {"id": "q2", "type": "question"},
				  {"id": "q3", "type": "question"}, {"id": "s2", "type": "section"}, {"id": "q4", "type": "question"},
				  {"id": "q5", "type": "question"}, {"id": "submit", "type": "submit"}],
				 "edges": [
				  {"from": "q1", "to": "q2", "kind": "default"},
				  {"from": "q2", "to": "q3", "kind": "default"},
				  {"from": "q3", "to": "q4", "kind": "default"},
				  {"from": "q4", "to": "q5", "kind": "default"},
				  {"from": "q5", "to": "submit", "kind": "default"}]}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void AC2_분기_그래프는_로그인이_없으면_401_제작자가_아니면_403이고_내용을_주지_않는다() throws Exception {
		String formId = createDraft(SampleForms.branched());

		mockMvc.perform(get("/forms/" + formId + "/branch-graph")).andExpect(status().isUnauthorized());
		expectForbiddenWithoutContent(branchGraph(other, formId));
		branchGraph(owner, UUID.randomUUID().toString()).andExpect(status().isNotFound());
	}

	private ResultActions qr(UUID user, String formId) throws Exception {
		return mockMvc.perform(get("/forms/" + formId + "/qr").with(user(user.toString())));
	}

	private String shareUrlOf(String formId) throws Exception {
		String body = mockMvc.perform(get("/forms/" + formId + "/definition").with(user(owner.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		return JSON.readTree(body).get("share_url").asString();
	}

	private static String decodeQr(byte[] png) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		// 서버가 만든 기울지 않은 깨끗한 이미지라 PURE_BARCODE로 읽는다. 기본 판독은 위치 찾기를 가끔 틀려
		// 무작위 링크 1000개 중 4개꼴로 ChecksumException을 냈다(PURE_BARCODE는 0개, 원문과 모두 같음).
		return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE)).getText();
	}

	@Test
	void qr_decodes_to_share_url() throws Exception {
		String formId = createDraft(SampleForms.unbranched());
		patchForm(owner, formId, Map.of("status", "open")).andExpect(status().isOk());
		String shareUrl = shareUrlOf(formId);

		byte[] png = qr(owner, formId).andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		assertThat(shareUrl).contains("/f/");
		assertThat(decodeQr(png)).isEqualTo(shareUrl);
	}

	@Test
	void qr_draft_409() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		qr(owner, formId).andExpect(status().isConflict())
			.andExpect(content().json("{\"error\":\"not_open\"}", JsonCompareMode.STRICT));
	}

	@Test
	void qr_decodes_to_share_url_draft로_되돌려도_공유_링크가_있으면_같은_링크다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());
		patchForm(owner, formId, Map.of("status", "open")).andExpect(status().isOk());
		patchForm(owner, formId, Map.of("status", "draft")).andExpect(status().isOk());

		byte[] png = qr(owner, formId).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertThat(decodeQr(png)).isEqualTo(shareUrlOf(formId));
	}

	private ResultActions copy(UUID user, String formId) throws Exception {
		return mockMvc.perform(post("/forms/" + formId + "/copy").with(user(user.toString())).with(csrf()));
	}

	private JsonNode definitionOf(String formId) throws Exception {
		return JSON.readTree(mockMvc.perform(get("/forms/" + formId + "/definition").with(user(owner.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8));
	}

	// 폼 정의만 남긴다. status·share_url·definition_version은 정의 밖의 값이다(SPEC 5절 116행).
	private static ObjectNode withoutState(JsonNode body) {
		ObjectNode definition = ((ObjectNode) body).deepCopy();
		definition.remove(List.of("status", "share_url", "definition_version"));
		return definition;
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	// 분기·설정(수정 허용, 인원, AI 요약 켬)이 있는 배포 폼에 응답 5건을 둔다.
	@SuppressWarnings("unchecked")
	private String openFormWithResponses() throws Exception {
		Map<String, Object> definition = SampleForms.branched();
		Map<String, Object> settings = (Map<String, Object>) definition.get("settings");
		settings.put("editable", true);
		settings.put("response_limit", 30);
		settings.put("ai_summary", new LinkedHashMap<>(Map.of("enabled", true)));
		String formId = createDraft(definition);
		patchForm(owner, formId, Map.of("status", "open")).andExpect(status().isOk());
		for (int i = 0; i < 5; i++) {
			responses.saveAndFlush(TestData.submitted(UUID.fromString(formId), null, 1, TestData.NOW));
		}
		return formId;
	}

	@Test
	void copy_form_without_responses() throws Exception {
		String sourceId = openFormWithResponses();
		JsonNode sourceBefore = definitionOf(sourceId);
		String noticeBefore = jdbc.queryForObject("select ai_summary_notice::text from forms where form_id = ?::uuid",
				String.class, sourceId);

		String body = copy(owner, sourceId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode created = JSON.readTree(body);
		String copyId = created.get("form_id").asString();
		assertThat(created.get("status").asString()).isEqualTo("draft");
		assertThat(created.properties()).hasSize(2);
		assertThat(copyId).isNotEqualTo(sourceId);

		JsonNode copied = definitionOf(copyId);
		assertThat(withoutState(copied)).isEqualTo(withoutState(sourceBefore));
		assertThat(copied.get("status").asString()).isEqualTo("draft");
		assertThat(copied.get("share_url").isNull()).isTrue();
		assertThat(copied.get("definition_version").isNull()).isTrue();
		assertThat(count("select count(*) from responses where form_id = ?::uuid", copyId)).isZero();
		assertThat(count("select count(*) from form_versions where form_id = ?::uuid", copyId)).isZero();
		assertThat(count("select count(*) from forms where form_id = ?::uuid and owner_id = ? and slug is null"
				+ " and ai_summary_notice is null", copyId, owner)).isEqualTo(1);

		assertThat(definitionOf(sourceId)).isEqualTo(sourceBefore);
		assertThat(count("select count(*) from responses where form_id = ?::uuid", sourceId)).isEqualTo(5);
		assertThat(jdbc.queryForObject("select ai_summary_notice::text from forms where form_id = ?::uuid", String.class,
				sourceId)).isEqualTo(noticeBefore).isNotNull();
	}

	@Test
	void copy_form_without_responses_AC90_복사본은_AI_요약을_바꿀_수_있고_원본은_그대로다() throws Exception {
		String sourceId = openFormWithResponses();
		JsonNode sourceBefore = definitionOf(sourceId);
		String copyId = JSON.readTree(copy(owner, sourceId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("form_id").asString();

		Map<String, Object> settings = new LinkedHashMap<>(
				Map.of("editable", true, "cancellable", false, "response_limit", 30, "display",
						Map.of("default_mode", "section_page"), "ai_summary", Map.of("enabled", false)));
		patchForm(owner, copyId, Map.of("settings", settings)).andExpect(status().isOk());
		patchForm(owner, copyId, Map.of("title", "복사본 제목")).andExpect(status().isOk());

		assertThat(definitionOf(copyId).at("/settings/ai_summary/enabled").asBoolean()).isFalse();
		assertThat(definitionOf(sourceId)).isEqualTo(sourceBefore);
		assertThat(sourceBefore.at("/settings/ai_summary/enabled").asBoolean()).isTrue();
	}

	@Test
	void AC2_복사는_로그인이_없으면_401_제작자가_아니면_403이고_폼을_만들지_않는다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());

		mockMvc.perform(post("/forms/" + formId + "/copy").with(csrf())).andExpect(status().isUnauthorized());
		expectForbiddenWithoutContent(copy(other, formId));
		copy(owner, UUID.randomUUID().toString()).andExpect(status().isNotFound());
		assertThat(count("select count(*) from forms where owner_id = ?", other)).isZero();
		assertThat(count("select count(*) from forms where owner_id = ?", owner)).isEqualTo(1);
	}

	@Test
	void AC2_QR은_로그인이_없으면_401_제작자가_아니면_403이고_내용을_주지_않는다() throws Exception {
		String formId = createDraft(SampleForms.unbranched());
		patchForm(owner, formId, Map.of("status", "open")).andExpect(status().isOk());

		mockMvc.perform(get("/forms/" + formId + "/qr")).andExpect(status().isUnauthorized());
		expectForbiddenWithoutContent(qr(other, formId));
	}

}
