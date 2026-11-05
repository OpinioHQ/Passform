package com.opinio.passform.repository;

import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.inbox.Notification;
import com.opinio.passform.domain.inbox.NotificationType;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormState;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;

// 유일 제약 6종. 다른 제약 때문에 거부된 것을 통과로 세지 않도록 제약 이름까지 확인한다.
@Transactional
class UniqueConstraintTest extends DatabaseTest {

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository formVersions;

	@Autowired
	private ResponseRepository responses;

	@Autowired
	private SavedFormRepository savedForms;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		userId = users.saveAndFlush(TestData.newUser()).getId();
		formId = newForm();
	}

	@Test
	void 내_폼함은_사용자와_폼의_조합이_유일하다() {
		savedForms.saveAndFlush(new SavedForm(userId, formId, SavedFormState.SAVED, NOW));
		entityManager.clear();

		assertRejectedBy("pk_saved_forms",
				() -> savedForms.saveAndFlush(new SavedForm(userId, formId, SavedFormState.SUBMITTED, NOW)));
	}

	@Test
	void 알림은_사용자와_폼과_종류의_조합이_유일하다() {
		notifications.saveAndFlush(new Notification(userId, formId, NotificationType.DEADLINE, NOW));
		entityManager.clear();

		assertRejectedBy("uq_notifications_user_form_type",
				() -> notifications.saveAndFlush(new Notification(userId, formId, NotificationType.DEADLINE, NOW)));
	}

	@Test
	void 폼_버전은_폼과_버전의_조합이_유일하고_save로_덮어쓰지_못한다() {
		formVersions.saveAndFlush(new FormVersion(formId, 1, Map.of("title", "처음"), NOW));
		entityManager.clear();

		assertRejectedBy("pk_form_versions",
				() -> formVersions.saveAndFlush(new FormVersion(formId, 1, Map.of("title", "덮어쓰기"), NOW)));
	}

	@Test
	void 폼_슬러그는_유일하다() {
		UUID otherFormId = newForm();
		jdbc.update("update forms set slug = 'same-slug' where form_id = ?", formId);

		assertRejectedBy("uq_forms_slug",
				() -> jdbc.update("update forms set slug = 'same-slug' where form_id = ?", otherFormId));
	}

	@Test
	void 초안_폼_여러_개는_슬러그가_비어_있어도_된다() {
		UUID otherFormId = newForm();

		assertThat(forms.findById(formId).orElseThrow().getSlug()).isNull();
		assertThat(forms.findById(otherFormId).orElseThrow().getSlug()).isNull();
	}

	@Test
	void 소셜_제공사와_고유_ID의_조합이_유일하다() {
		User first = users.saveAndFlush(new User("가상", "a@example.com", "google", "same-subject", NOW));
		assertThat(first.getId()).isNotNull();

		assertRejectedBy("uq_users_oauth",
				() -> users.saveAndFlush(new User("가상", "b@example.com", "google", "same-subject", NOW)));
	}

	@Test
	void 로그인_작성_중_응답은_사용자의_폼당_하나다() {
		responses.saveAndFlush(TestData.withStatus(formId, userId, ResponseStatus.IN_PROGRESS));

		assertRejectedBy("uq_responses_login_draft",
				() -> responses.saveAndFlush(TestData.withStatus(formId, userId, ResponseStatus.IN_PROGRESS)));
	}

	@Test
	void 부분_인덱스는_제출_여러_건과_제출_뒤_새_초안을_막지_않는다_AC51() {
		responses.saveAndFlush(TestData.submitted(formId, userId, 1, NOW));
		responses.saveAndFlush(TestData.submitted(formId, userId, 1, NOW));
		responses.saveAndFlush(TestData.withStatus(formId, userId, ResponseStatus.IN_PROGRESS));

		assertThat(responses.count()).isEqualTo(3);
	}

	@Test
	void 부분_인덱스는_비로그인_응답과_다른_사용자와_다른_폼을_막지_않는다() {
		UUID otherUserId = users.saveAndFlush(TestData.newUser()).getId();
		UUID otherFormId = newForm();

		responses.saveAndFlush(TestData.withStatus(formId, null, ResponseStatus.IN_PROGRESS));
		responses.saveAndFlush(TestData.withStatus(formId, null, ResponseStatus.IN_PROGRESS));
		responses.saveAndFlush(TestData.withStatus(formId, userId, ResponseStatus.IN_PROGRESS));
		responses.saveAndFlush(TestData.withStatus(formId, otherUserId, ResponseStatus.IN_PROGRESS));
		responses.saveAndFlush(TestData.withStatus(otherFormId, userId, ResponseStatus.IN_PROGRESS));

		assertThat(responses.count()).isEqualTo(5);
	}

	private UUID newForm() {
		return forms.saveAndFlush(new Form(userId, Map.of("title", "폼"), NOW)).getId();
	}

	private static void assertRejectedBy(String constraint, Runnable duplicate) {
		assertThatThrownBy(duplicate::run).isInstanceOf(DataIntegrityViolationException.class)
			.rootCause()
			.hasMessageContaining(constraint);
	}

}
