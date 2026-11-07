package com.opinio.passform.service;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

// 동시 제출에서 선착순 인원을 넘지 않는다(AC85, ARCHITECTURE 통합 시험 결정). 실제 Postgres에서 실행하므로 @Transactional을 붙이지 않는다.
class ResponseLimitConcurrencyTest extends DatabaseTest {

	// 잠금이 없으면 타이밍에 따라 통과할 수 있어 라운드를 반복한다.
	private static final int ROUNDS = 15;

	private static final int CONCURRENT_SUBMITS = 6;

	private static final int LIMIT = 3;

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
	private ResponseRepository responses;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void response_limit_concurrent_submit() throws Exception {
		UUID ownerId = users.saveAndFlush(TestData.newUser()).getId();
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_SUBMITS);
		try {
			for (int round = 0; round < ROUNDS; round++) {
				UUID formId = formWithOneSeatLeft(ownerId);

				List<MockHttpServletResponse> results = submitTogether(pool, formId);

				List<Integer> statuses = results.stream().map(MockHttpServletResponse::getStatus).toList();
				assertThat(statuses).as("라운드 %d", round).containsOnlyOnce(201);
				for (MockHttpServletResponse result : results) {
					if (result.getStatus() != 201) {
						assertThat(result.getStatus()).as("라운드 %d", round).isEqualTo(409);
						assertThat(result.getContentAsString()).isEqualTo("{\"error\":\"form_closed\"}");
					}
				}
				assertThat(responses.countSubmittedNotDeleted(formId)).as("라운드 %d", round).isEqualTo(LIMIT);
				assertThat(forms.findById(formId).orElseThrow().getStatus()).isEqualTo(FormStatus.CLOSED);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	private UUID formWithOneSeatLeft(UUID ownerId) {
		UUID formId = forms.saveAndFlush(new Form(ownerId, DEFINITION, NOW)).getId();
		versions.saveAndFlush(new FormVersion(formId, 1, DEFINITION, NOW));
		jdbc.update("update forms set status = 'OPEN', definition_version = 1, response_limit = ? where form_id = ?", LIMIT,
				formId);
		for (int i = 0; i < LIMIT - 1; i++) {
			responses.saveAndFlush(TestData.submitted(formId, null, 1, NOW));
		}
		return formId;
	}

	private List<MockHttpServletResponse> submitTogether(ExecutorService pool, UUID formId) throws Exception {
		CountDownLatch ready = new CountDownLatch(CONCURRENT_SUBMITS);
		CountDownLatch start = new CountDownLatch(1);
		Callable<MockHttpServletResponse> submit = () -> {
			ready.countDown();
			assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
			return mockMvc.perform(post("/forms/" + formId + "/responses").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"form_version\":1,\"answers\":[{\"question_id\":\"q1\",\"value\":\"값\"}],\"consent_agreed\":true}"))
				.andReturn()
				.getResponse();
		};
		List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_SUBMITS; i++) {
			futures.add(pool.submit(submit));
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
