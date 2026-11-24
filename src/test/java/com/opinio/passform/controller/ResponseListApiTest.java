package com.opinio.passform.controller;

import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.SeedForms.Seeded;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

// GET /forms/{form_id}/responses(응답별 보기의 목록, AC19·AC62)를 실제 Postgres와 data/seed의 form_b로 시험한다.
// 기대 순서는 fixture의 submitted_at을 손으로 오름차순 정렬한 것이다.
@Transactional
class ResponseListApiTest extends DatabaseTest {

	// form_b 제출 14건의 submitted_at 오름차순. rb15 in_progress, rb16 abandoned는 없다.
	private static final List<String> FORM_B_ORDER = List.of("rb03", "rb06", "rb14", "rb02", "rb05", "rb09", "rb10",
			"rb07", "rb01", "rb11", "rb08", "rb13", "rb12", "rb04");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository formVersions;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID otherId;

	private Seeded formB;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		ownerId = users.saveAndFlush(TestData.newUser()).getId();
		otherId = users.saveAndFlush(TestData.newUser()).getId();
		formB = SeedForms.load("form_b_mixed.json", ownerId, forms, formVersions, responses);
	}

	private ResultActions getAs(UUID userId, String path) throws Exception {
		return mockMvc.perform(get(path).with(user(userId.toString())));
	}

	private static String listPath(Object formId) {
		return "/forms/" + formId + "/responses";
	}

	@SuppressWarnings("unchecked")
	private List<String> listedIds() throws Exception {
		String body = getAs(ownerId, listPath(formB.formId())).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> json = JsonMapper.builder().build().readValue(body, Map.class);
		assertThat(json).containsOnlyKeys("response_ids");
		return (List<String>) json.get("response_ids");
	}

	private List<String> idsOf(List<String> seedIds) {
		return seedIds.stream().map(seedId -> formB.response(seedId).toString()).toList();
	}

	// JDBC로 바꾼 값을 읽도록 같은 트랜잭션의 1차 캐시를 비운다.
	private void setSubmittedAt(String seedId, Instant at) {
		jdbc.update("update responses set submitted_at = ? where response_id = ?", Timestamp.from(at),
				formB.response(seedId));
		entityManager.clear();
	}

	@Test
	void 목록은_제출_시각_오름차순이다() throws Exception {
		assertThat(listedIds()).containsExactlyElementsOf(idsOf(FORM_B_ORDER));
	}

	// 버전 순서(쿼리 순서)가 아니라 제출 시각 순서다. v3의 rb04를 가장 이르게 바꾸면 맨 앞으로 온다.
	@Test
	void 여러_버전이_섞여도_제출_시각_순서다() throws Exception {
		setSubmittedAt("rb04", Instant.parse("2026-10-01T00:00:00Z"));

		List<String> expected = new ArrayList<>(List.of("rb04"));
		expected.addAll(FORM_B_ORDER.subList(0, FORM_B_ORDER.size() - 1));
		assertThat(listedIds()).containsExactlyElementsOf(idsOf(expected));
	}

	// 제출 시각이 같으면 응답 ID 오름차순(DB uuid 순서와 같은 문자열 순서)이다.
	// 세 응답의 버전 순서는 rb02(v1)·rb10(v2)·rb13(v3), 부호 있는 UUID 비교 순서는 8…·f…·1…이라 둘 다 기대 순서와 다르다.
	@Test
	void 제출_시각이_같으면_응답_ID_오름차순이다() throws Exception {
		Instant same = Clock.fixed(NOW, ZoneOffset.UTC).instant();
		UUID first = UUID.fromString("10000000-0000-4000-8000-000000000000");
		UUID second = UUID.fromString("80000000-0000-4000-8000-000000000000");
		UUID third = UUID.fromString("f0000000-0000-4000-8000-000000000000");
		Map<String, UUID> newIds = Map.of("rb13", first, "rb10", second, "rb02", third);
		for (Map.Entry<String, UUID> entry : newIds.entrySet()) {
			jdbc.update("update responses set response_id = ?, submitted_at = ? where response_id = ?", entry.getValue(),
					Timestamp.from(same), formB.response(entry.getKey()));
		}
		entityManager.clear();

		List<String> listed = listedIds();
		List<String> rest = idsOf(FORM_B_ORDER.stream().filter(seedId -> !newIds.containsKey(seedId)).toList());
		assertThat(listed.subList(0, rest.size())).containsExactlyElementsOf(rest);
		assertThat(listed.subList(rest.size(), listed.size())).containsExactly(first.toString(), second.toString(),
				third.toString());
	}

	// AC20: in_progress(rb15)·abandoned(rb16)·제작자 삭제(rb01) 응답은 빠지고, 수는 /results의 response_count와 같다.
	@Test
	void 제출되지_않았거나_삭제된_응답은_빠지고_수는_결과와_같다() throws Exception {
		jdbc.update("update responses set creator_deleted_at = ? where response_id = ?", Timestamp.from(NOW),
				formB.response("rb01"));
		entityManager.clear();

		List<String> listed = listedIds();
		assertThat(listed).doesNotContain(formB.response("rb15").toString(), formB.response("rb16").toString(),
				formB.response("rb01").toString());
		assertThat(listed)
			.containsExactlyElementsOf(idsOf(FORM_B_ORDER.stream().filter(seedId -> !seedId.equals("rb01")).toList()));
		String results = getAs(ownerId, "/forms/" + formB.formId() + "/results").andReturn()
			.getResponse()
			.getContentAsString();
		Object count = JsonMapper.builder().build().readValue(results, Map.class).get("response_count");
		assertThat(listed).hasSize(((Number) count).intValue());
	}

	@Test
	void 응답이_없으면_빈_목록이다() throws Exception {
		UUID empty = forms.saveAndFlush(new Form(ownerId, Map.of("title", "초안"), NOW)).getId();

		getAs(ownerId, listPath(empty)).andExpect(status().isOk())
			.andExpect(content().json("{\"response_ids\":[]}", JsonCompareMode.STRICT));
	}

	// AC62 403, AC2: 남의 폼은 응답 ID 없이 403
	@Test
	void 다른_사용자가_제작한_폼은_403이다() throws Exception {
		getAs(otherId, listPath(formB.formId())).andExpect(status().isForbidden())
			.andExpect(content().json("{\"error\":\"forbidden\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 없거나_삭제된_폼과_UUID가_아닌_폼_ID는_404다() throws Exception {
		getAs(ownerId, listPath(UUID.randomUUID())).andExpect(status().isNotFound())
			.andExpect(content().json("{\"error\":\"not_found\"}", JsonCompareMode.STRICT));
		getAs(ownerId, listPath("not-a-uuid")).andExpect(status().isNotFound());

		jdbc.update("update forms set deleted_at = ? where form_id = ?", Timestamp.from(NOW), formB.formId());
		entityManager.clear();
		for (UUID userId : List.of(ownerId, otherId)) {
			getAs(userId, listPath(formB.formId())).andExpect(status().isNotFound());
		}
	}

	@Test
	void 비로그인이면_401이다() throws Exception {
		mockMvc.perform(get(listPath(formB.formId()))).andExpect(status().isUnauthorized());
	}

}
