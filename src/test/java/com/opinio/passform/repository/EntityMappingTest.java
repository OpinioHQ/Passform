package com.opinio.passform.repository;

import static com.opinio.passform.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.inbox.Notification;
import com.opinio.passform.domain.inbox.NotificationType;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;
import com.opinio.passform.domain.inbox.SavedFormState;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.template.ConsentTemplate;
import com.opinio.passform.domain.template.QuestionTemplate;
import com.opinio.passform.domain.user.Profile;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;

// 엔티티마다 저장 후 다시 읽어 컬럼 매핑을 확인한다. 상태 전이 메서드가 아직 없으므로 나머지 컬럼은 SQL로 채운다.
@Transactional
class EntityMappingTest extends DatabaseTest {

	@Autowired
	private UserRepository users;

	@Autowired
	private ProfileRepository profiles;

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
	private ConsentTemplateRepository consentTemplates;

	@Autowired
	private QuestionTemplateRepository questionTemplates;

	@Autowired
	private AttachmentRepository attachments;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;

	private UUID formId;

	@BeforeEach
	void setUp() {
		userId = users.saveAndFlush(TestData.newUser()).getId();
		formId = forms.saveAndFlush(new Form(userId, Map.of("title", "폼"), NOW)).getId();
	}

	@Test
	void users() {
		entityManager.clear();

		User user = users.findById(userId).orElseThrow();

		assertThat(user.getName()).isEqualTo("가상 사용자");
		assertThat(user.getEmail()).isEqualTo("virtual@example.com");
		assertThat(user.isDeadlineReminder()).isTrue();
		assertThat(user.getOauthProvider()).isEqualTo("google");
		assertThat(user.getOauthSubject()).isNotBlank();
		assertThat(user.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void profiles() {
		UUID id = profiles.saveAndFlush(new Profile(userId, "학교", Map.of("birth_date", "2000-01-01"), NOW)).getId();
		entityManager.clear();

		Profile profile = profiles.findById(id).orElseThrow();

		assertThat(profile.getUserId()).isEqualTo(userId);
		assertThat(profile.getLabel()).isEqualTo("학교");
		assertThat(profile.getPersonalInfo()).containsEntry("birth_date", "2000-01-01");
		assertThat(profile.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void forms_새_폼은_draft이고_버전과_슬러그가_없다_AC38() {
		entityManager.clear();

		Form form = forms.findById(formId).orElseThrow();

		assertThat(form.getOwnerId()).isEqualTo(userId);
		assertThat(form.getStatus()).isEqualTo(FormStatus.DRAFT);
		assertThat(form.getSlug()).isNull();
		assertThat(form.getDefinitionVersion()).isNull();
		assertThat(form.getDeadline()).isNull();
		assertThat(form.isEditable()).isFalse();
		assertThat(form.isCancellable()).isFalse();
		assertThat(form.getResponseLimit()).isNull();
		assertThat(form.getWorkingDefinition()).containsEntry("title", "폼");
		assertThat(form.getAiSummaryNotice()).isNull();
		assertThat(form.getDeletedAt()).isNull();
		assertThat(form.getCreatedAt()).isEqualTo(NOW);
		assertThat(form.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void forms_나머지_컬럼() {
		Timestamp at = Timestamp.from(NOW);
		jdbc.update("""
				update forms set status = 'OPEN', slug = 'a-slug', definition_version = 2, deadline = ?, editable = true,
				cancellable = true, response_limit = 5, working_definition = null,
				ai_summary_notice = '{"provider":"OpenAI"}', deleted_at = ? where form_id = ?
				""", at, at, formId);
		entityManager.clear();

		Form form = forms.findById(formId).orElseThrow();

		assertThat(form.getStatus()).isEqualTo(FormStatus.OPEN);
		assertThat(form.getSlug()).isEqualTo("a-slug");
		assertThat(form.getDefinitionVersion()).isEqualTo(2);
		assertThat(form.getDeadline()).isEqualTo(NOW);
		assertThat(form.isEditable()).isTrue();
		assertThat(form.isCancellable()).isTrue();
		assertThat(form.getResponseLimit()).isEqualTo(5);
		assertThat(form.getWorkingDefinition()).isNull();
		assertThat(form.getAiSummaryNotice()).containsEntry("provider", "OpenAI");
		assertThat(form.getDeletedAt()).isEqualTo(NOW);
	}

	@Test
	void form_versions() {
		FormVersion saved = formVersions.saveAndFlush(new FormVersion(formId, 1, Map.of("title", "v1"), NOW));
		assertThat(saved.isNew()).isFalse();
		entityManager.clear();

		FormVersion version = formVersions.findById(new FormVersionId(formId, 1)).orElseThrow();

		assertThat(version.getId()).isEqualTo(new FormVersionId(formId, 1));
		assertThat(version.isNew()).isFalse();
		assertThat(version.getFormId()).isEqualTo(formId);
		assertThat(version.getVersion()).isEqualTo(1);
		assertThat(version.getDefinition()).containsEntry("title", "v1");
		assertThat(version.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void responses_저장_시각은_UTC_그대로_돌아온다() {
		UUID id = responses.saveAndFlush(TestData.withStatus(formId, userId, ResponseStatus.IN_PROGRESS)).getId();
		Timestamp at = Timestamp.from(NOW);
		jdbc.update("""
				update responses set updated_at = ?, creator_deleted_at = ?, history_form_title = '이력 제목',
				history_questions = '[{"question_id":"q1","question_note":"학과"}]', ai_summary_agreed = true
				where response_id = ?
				""", at, at, id);
		entityManager.clear();

		Response response = responses.findById(id).orElseThrow();

		assertThat(response.getFormId()).isEqualTo(formId);
		assertThat(response.getUserId()).isEqualTo(userId);
		assertThat(response.getFormVersion()).isEqualTo(1);
		assertThat(response.getStatus()).isEqualTo(ResponseStatus.IN_PROGRESS);
		assertThat(response.getContent()).isEmpty();
		assertThat(response.isAiSummaryAgreed()).isTrue();
		assertThat(response.getSavedAt()).isEqualTo(NOW);
		assertThat(response.getSubmittedAt()).isNull();
		assertThat(response.getUpdatedAt()).isEqualTo(NOW);
		assertThat(response.getCreatorDeletedAt()).isEqualTo(NOW);
		assertThat(response.getHistoryFormTitle()).isEqualTo("이력 제목");
		assertThat(response.getHistoryQuestions()).containsExactly(Map.of("question_id", "q1", "question_note", "학과"));
	}

	@Test
	void saved_forms() {
		SavedForm saved = savedForms.saveAndFlush(new SavedForm(userId, formId, SavedFormState.SAVED, NOW));
		assertThat(saved.isNew()).isFalse();
		entityManager.clear();

		SavedForm savedForm = savedForms.findById(new SavedFormId(userId, formId)).orElseThrow();

		assertThat(savedForm.getId()).isEqualTo(new SavedFormId(userId, formId));
		assertThat(savedForm.getUserId()).isEqualTo(userId);
		assertThat(savedForm.getFormId()).isEqualTo(formId);
		assertThat(savedForm.getState()).isEqualTo(SavedFormState.SAVED);
		assertThat(savedForm.getSavedAt()).isEqualTo(NOW);
	}

	@Test
	void notifications() {
		UUID id = notifications.saveAndFlush(new Notification(userId, formId, NotificationType.DEADLINE, NOW)).getId();
		entityManager.clear();

		Notification notification = notifications.findById(id).orElseThrow();

		assertThat(notification.getUserId()).isEqualTo(userId);
		assertThat(notification.getFormId()).isEqualTo(formId);
		assertThat(notification.getType()).isEqualTo(NotificationType.DEADLINE);
		assertThat(notification.isRead()).isFalse();
		assertThat(notification.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void consent_templates() {
		consentTemplates.saveAndFlush(new ConsentTemplate("test_consent", "목적", "보유 기간"));
		entityManager.clear();

		ConsentTemplate template = consentTemplates.findById("test_consent").orElseThrow();

		assertThat(template.getId()).isEqualTo("test_consent");
		assertThat(template.getPurpose()).isEqualTo("목적");
		assertThat(template.getRetention()).isEqualTo("보유 기간");
	}

	@Test
	void question_templates() {
		questionTemplates.saveAndFlush(new QuestionTemplate("test_template", "예약", "대학생", "예약 날짜", "short_answer",
				List.of("가", "나"), Map.of("kind", "date")));
		entityManager.clear();

		QuestionTemplate template = questionTemplates.findById("test_template").orElseThrow();

		assertThat(template.getId()).isEqualTo("test_template");
		assertThat(template.getPurpose()).isEqualTo("예약");
		assertThat(template.getAudience()).isEqualTo("대학생");
		assertThat(template.getQuestionNote()).isEqualTo("예약 날짜");
		assertThat(template.getQuestionType()).isEqualTo("short_answer");
		assertThat(template.getOptions()).containsExactly("가", "나");
		assertThat(template.getInputFormat()).containsEntry("kind", "date");
	}

	@Test
	void attachments() {
		UUID responseId = responses.saveAndFlush(TestData.submitted(formId, null, 1, NOW)).getId();
		UUID id = attachments
			.saveAndFlush(new Attachment(formId, responseId, "q1", "a.pdf", "application/pdf", 25_000_000L, "key/a", NOW))
			.getId();
		entityManager.clear();

		Attachment attachment = attachments.findById(id).orElseThrow();

		assertThat(attachment.getFormId()).isEqualTo(formId);
		assertThat(attachment.getResponseId()).isEqualTo(responseId);
		assertThat(attachment.getQuestionId()).isEqualTo("q1");
		assertThat(attachment.getFilename()).isEqualTo("a.pdf");
		assertThat(attachment.getContentType()).isEqualTo("application/pdf");
		assertThat(attachment.getSizeBytes()).isEqualTo(25_000_000L);
		assertThat(attachment.getS3Key()).isEqualTo("key/a");
		assertThat(attachment.isAvailable()).isTrue();
		assertThat(attachment.getCreatedAt()).isEqualTo(NOW);
	}

}
