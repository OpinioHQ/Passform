package com.opinio.passform.service;

import static com.opinio.passform.support.FormDefinitions.definition;
import static com.opinio.passform.support.FormDefinitions.section;
import static com.opinio.passform.support.FormDefinitions.text;
import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.response.Answer;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

// 같은 응답의 동시 취소·수정에서 500이 나지 않고 응답 수가 어긋나지 않는다. 실제 Postgres에서 실행하므로 @Transactional을 붙이지 않는다.
class ResponseChangeConcurrencyTest extends DatabaseTest {

	private static final int ROUNDS = 10;

	private static final int CONCURRENT = 4;

	private static final Map<String, Object> DEFINITION = definition("one_question",
			List.of(section("s1", text("q1", true))));

	private static final String BODY = "{\"answers\":[{\"question_id\":\"q1\",\"value\":\"수정\"}]}";

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

	private UUID userId;

	private RequestPostProcessor login;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		userId = users.saveAndFlush(TestData.newUser()).getId();
		login = user(userId.toString());
	}

	private UUID openForm() {
		UUID formId = forms.saveAndFlush(new Form(userId, DEFINITION, NOW)).getId();
		versions.saveAndFlush(new FormVersion(formId, 1, DEFINITION, NOW));
		jdbc.update("update forms set status = 'OPEN', definition_version = 1, editable = true, cancellable = true where form_id = ?",
				formId);
		return formId;
	}

	private Response submitted(UUID formId) {
		return responses.saveAndFlush(new Response(formId, userId, 1, ResponseStatus.SUBMITTED,
				new ArrayList<>(List.of(new Answer("q1", "처음"))), false, null, NOW));
	}

	private Callable<MockHttpServletResponse> cancelCall(UUID responseId) {
		return () -> mockMvc.perform(delete("/me/responses/" + responseId).with(login).with(csrf())).andReturn().getResponse();
	}

	private Callable<MockHttpServletResponse> editCall(UUID responseId) {
		return () -> mockMvc.perform(patch("/me/responses/" + responseId).with(login)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andReturn().getResponse();
	}

	private List<Integer> together(ExecutorService pool, List<Callable<MockHttpServletResponse>> calls) throws Exception {
		CountDownLatch ready = new CountDownLatch(calls.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
		for (Callable<MockHttpServletResponse> call : calls) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
				return call.call();
			}));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<MockHttpServletResponse> future : futures) {
			statuses.add(future.get(60, TimeUnit.SECONDS).getStatus());
		}
		return statuses;
	}

	@Test
	void 같은_응답을_동시에_취소하면_한_건만_성공하고_나머지는_404다() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT);
		try {
			for (int round = 0; round < ROUNDS; round++) {
				UUID formId = openForm();
				Response target = submitted(formId);
				submitted(formId);
				List<Callable<MockHttpServletResponse>> calls = new ArrayList<>();
				for (int i = 0; i < CONCURRENT; i++) {
					calls.add(cancelCall(target.getId()));
				}

				List<Integer> statuses = together(pool, calls);

				assertThat(statuses).as("라운드 %d: %s", round, statuses).containsOnlyOnce(204);
				assertThat(statuses).as("라운드 %d", round).containsOnly(204, 404);
				assertThat(responses.countSubmittedNotDeleted(formId)).as("라운드 %d", round).isEqualTo(1);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 수정과_취소가_동시에_와도_500이_나지_않고_응답_수가_맞는다() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT);
		try {
			for (int round = 0; round < ROUNDS; round++) {
				UUID formId = openForm();
				Response target = submitted(formId);
				submitted(formId);

				List<Integer> statuses = together(pool,
						List.of(editCall(target.getId()), cancelCall(target.getId()), editCall(target.getId()),
								cancelCall(target.getId())));

				assertThat(statuses).as("라운드 %d: %s", round, statuses).containsOnlyOnce(204);
				assertThat(statuses).as("라운드 %d", round).allMatch(status -> status == 200 || status == 204 || status == 404);
				assertThat(responses.findById(target.getId())).as("라운드 %d", round).isEmpty();
				assertThat(responses.countSubmittedNotDeleted(formId)).as("라운드 %d", round).isEqualTo(1);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

}
