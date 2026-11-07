package com.opinio.passform.service;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

// 폼당 계정 초안 한 건(SPEC 5절 206행, AC83). 같은 사용자의 첫 저장이 동시에 와도 초안은 한 건이고 500이 나지 않는다.
// 실제 Postgres에서 커밋해 보므로 @Transactional을 붙이지 않는다.
class ResponseDraftConcurrencyTest extends DatabaseTest {

	// 잠금이 없으면 타이밍에 따라 통과할 수 있어 라운드를 반복한다.
	private static final int ROUNDS = 10;

	private static final int CONCURRENT_SAVES = 6;

	private static final Map<String, Object> DEFINITION = definition("one_question",
			List.of(section("s1", text("q1", true))));

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository versions;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void 동시_첫_저장에도_초안과_내_폼함_항목은_한_건이다() throws Exception {
		UUID ownerId = users.saveAndFlush(TestData.newUser()).getId();
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_SAVES);
		try {
			for (int round = 0; round < ROUNDS; round++) {
				UUID formId = openForm(ownerId);
				UUID userId = users.saveAndFlush(TestData.newUser()).getId();

				List<MockHttpServletResponse> results = saveTogether(pool, formId, userId);

				List<Integer> statuses = results.stream().map(MockHttpServletResponse::getStatus).toList();
				assertThat(statuses).as("라운드 %d", round).containsOnlyOnce(201).allMatch(s -> s == 201 || s == 200);
				assertThat(results.stream().map(r -> responseIdOf(r)).distinct()).as("라운드 %d", round).hasSize(1);
				assertThat(jdbc.queryForObject("select count(*) from responses where user_id = ? and form_id = ?",
						Integer.class, userId, formId)).as("라운드 %d", round).isEqualTo(1);
				assertThat(jdbc.queryForObject("select count(*) from saved_forms where user_id = ? and form_id = ?",
						Integer.class, userId, formId)).as("라운드 %d", round).isEqualTo(1);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static String responseIdOf(MockHttpServletResponse response) {
		try {
			String body = response.getContentAsString();
			int start = body.indexOf("\"response_id\":\"") + "\"response_id\":\"".length();
			return body.substring(start, body.indexOf('"', start));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private UUID openForm(UUID ownerId) {
		UUID formId = forms.saveAndFlush(new Form(ownerId, DEFINITION, NOW)).getId();
		versions.saveAndFlush(new FormVersion(formId, 1, DEFINITION, NOW));
		jdbc.update("update forms set status = 'OPEN', definition_version = 1 where form_id = ?", formId);
		return formId;
	}

	private List<MockHttpServletResponse> saveTogether(ExecutorService pool, UUID formId, UUID userId) throws Exception {
		CountDownLatch ready = new CountDownLatch(CONCURRENT_SAVES);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_SAVES; i++) {
			String value = "값 " + i;
			Callable<MockHttpServletResponse> save = () -> {
				ready.countDown();
				assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
				return mockMvc.perform(put("/me/forms/" + formId + "/response-draft").with(csrf())
					.with(user(userId.toString()))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"" + value + "\"}]}"))
					.andReturn()
					.getResponse();
			};
			futures.add(pool.submit(save));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<MockHttpServletResponse> results = new ArrayList<>();
		for (Future<MockHttpServletResponse> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}
		return results;
	}

}
