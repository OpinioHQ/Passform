package com.opinio.passform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.opinio.passform.domain.inbox.Notification;
import com.opinio.passform.domain.inbox.NotificationType;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.NotificationRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.TestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 알림 목록·읽음 처리(SPEC 5절 201~202행, AC18). 본인 것만 보이고 남의 알림 ID는 404, 로그인이 없으면 401이다.
@Transactional
class NotificationApiTest extends DatabaseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

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
	private NotificationRepository notifications;

	private MockMvc mockMvc;

	private UUID userId;

	private UUID otherId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		userId = users.saveAndFlush(TestData.newUser()).getId();
		otherId = users.saveAndFlush(TestData.newUser()).getId();
		formId = seedForm();
	}

	private UUID seedForm() {
		return SeedForms.load("form_required_branch.json", otherId, forms, formVersions, responses).formId();
	}

	private Notification notify(UUID owner, UUID form, Instant createdAt) {
		return notifications.saveAndFlush(new Notification(owner, form, NotificationType.DEADLINE, createdAt));
	}

	private ResultActions markRead(UUID actor, Object notificationId, String body) throws Exception {
		return mockMvc.perform(patch("/me/notifications/" + notificationId).with(user(actor.toString()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private JsonNode list(UUID actor) throws Exception {
		return JSON.readTree(mockMvc.perform(get("/me/notifications").with(user(actor.toString())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	@Test
	void 목록은_본인_알림만_created_at_내림차순_같으면_id_오름차순이고_SPEC_모양이다() throws Exception {
		Instant later = TestData.NOW.plus(Duration.ofHours(1));
		Notification oldest = notify(userId, formId, TestData.NOW);
		Notification tieA = notify(userId, seedForm(), later);
		Notification tieB = notify(userId, seedForm(), later);
		notify(otherId, formId, later);

		JsonNode body = list(userId);

		List<String> ids = new ArrayList<>();
		body.forEach(item -> ids.add(item.get("notification_id").asString()));
		List<String> ties = new ArrayList<>(List.of(tieA.getId().toString(), tieB.getId().toString()));
		ties.sort(null);
		assertThat(ids).containsExactly(ties.get(0), ties.get(1), oldest.getId().toString());
		JsonNode last = body.get(2);
		assertThat(last.propertyNames()).containsExactlyInAnyOrder("notification_id", "form_id", "type", "created_at",
				"read");
		assertThat(last.get("form_id").asString()).isEqualTo(formId.toString());
		assertThat(last.get("type").asString()).isEqualTo("deadline");
		assertThat(Instant.parse(last.get("created_at").asString())).isEqualTo(TestData.NOW);
		assertThat(last.get("read").asBoolean()).isFalse();
	}

	@Test
	void 읽음_처리는_200이고_다시_해도_200이다() throws Exception {
		Notification mine = notify(userId, formId, TestData.NOW);

		markRead(userId, mine.getId(), "{\"read\":true}").andExpect(status().isOk());
		markRead(userId, mine.getId(), "{\"read\":true}").andExpect(status().isOk());

		assertThat(list(userId).get(0).get("read").asBoolean()).isTrue();
	}

	@Test
	void 남의_알림이나_없는_알림이나_잘못된_ID는_404이고_바꾸지_않는다() throws Exception {
		Notification others = notify(otherId, formId, TestData.NOW);

		markRead(userId, others.getId(), "{\"read\":true}").andExpect(status().isNotFound())
			.andExpect(content().json("{\"error\":\"not_found\"}", JsonCompareMode.STRICT));
		markRead(userId, UUID.randomUUID(), "{\"read\":true}").andExpect(status().isNotFound());
		markRead(userId, "not-a-uuid", "{\"read\":true}").andExpect(status().isNotFound());

		assertThat(notifications.findById(others.getId()).orElseThrow().isRead()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"read\":false}", "{}", "{\"read\":null}", "{\"read\":\"true\"}", "{\"read\":1}" })
	void 본문이_read_true가_아니면_invalid_request이고_바꾸지_않는다(String body) throws Exception {
		Notification mine = notify(userId, formId, TestData.NOW);

		markRead(userId, mine.getId(), body).andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"invalid_request\"}", JsonCompareMode.STRICT));

		assertThat(notifications.findById(mine.getId()).orElseThrow().isRead()).isFalse();
	}

	@Test
	void 로그인이_없으면_401이다() throws Exception {
		Notification mine = notify(userId, formId, TestData.NOW);

		mockMvc.perform(get("/me/notifications")).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/me/notifications/" + mine.getId()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"read\":true}")).andExpect(status().isUnauthorized());
	}

}
