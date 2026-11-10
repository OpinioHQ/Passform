package com.opinio.passform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.inbox.Notification;
import com.opinio.passform.domain.inbox.NotificationType;
import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.mail.DeadlineReminderMail;
import com.opinio.passform.mail.ReminderMailer;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.NotificationRepository;
import com.opinio.passform.repository.ResponseRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SeedForms;
import com.opinio.passform.support.TestData;

// 마감 알림 스케줄러(AC18, SPEC 497행의 테스트 문장)를 실제 Postgres와 고정 시계로 시험한다. 메일은 모의 객체다.
// 트랜잭션을 걸지 않는다: 알림 행은 커밋된 뒤에만 메일을 보내고, 동시 실행은 다른 트랜잭션이어야 하기 때문이다.
// 시험마다 새 사용자·폼을 만들고 그 사용자의 알림과 받는 주소로만 판정한다.
class DeadlineReminderServiceTest extends DatabaseTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockOverride {

		@Bean
		@Primary
		MovableClock movableClock() {
			return new MovableClock();
		}

	}

	static final class MovableClock extends Clock {

		private volatile Instant now = TestData.NOW;

		void set(Instant instant) {
			now = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@MockitoBean
	private ReminderMailer mailer;

	@Autowired
	private MovableClock clock;

	@Autowired
	private DeadlineReminderService reminders;

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

	@BeforeEach
	void setUp() {
		clock.set(TestData.NOW);
	}

	private User user(boolean reminder) {
		String subject = UUID.randomUUID().toString();
		User user = new User("가상 사용자", "r-" + subject + "@example.com", "google", subject, TestData.NOW);
		user.changeDeadlineReminder(reminder);
		return users.saveAndFlush(user);
	}

	private UUID openForm(Instant deadline) {
		UUID formId = SeedForms.load("form_required_branch.json", user(true).getId(), forms, formVersions, responses)
			.formId();
		Form form = forms.findById(formId).orElseThrow();
		ReflectionTestUtils.setField(form, "deadline", deadline);
		ReflectionTestUtils.setField(form, "slug", "r-" + formId);
		forms.saveAndFlush(form);
		return formId;
	}

	private void save(User user, UUID formId) {
		savedForms.saveAndFlush(SavedForm.saved(user.getId(), formId, clock.instant()));
	}

	private List<Notification> notificationsOf(User user) {
		return notifications.findByUserIdOrderByCreatedAtDescIdAsc(user.getId());
	}

	private void verifyMails(User user, int count) {
		verify(mailer, times(count)).send(argThat(mail -> mail.to().equals(user.getEmail())));
	}

	@Test
	void deadline_reminder_once_마감_23시간_전_2회_실행해도_미제출_알림켬_사용자에게만_1건() {
		Instant deadline = TestData.NOW.plus(Duration.ofHours(23));
		UUID formId = openForm(deadline);
		User pending = user(true);
		User submitted = user(true);
		User optedOut = user(false);
		save(pending, formId);
		savedForms.saveAndFlush(SavedForm.submitted(submitted.getId(), formId, clock.instant()));
		save(optedOut, formId);

		reminders.run();
		reminders.run();

		assertThat(notificationsOf(pending)).singleElement().satisfies(notification -> {
			assertThat(notification.getFormId()).isEqualTo(formId);
			assertThat(notification.getType()).isEqualTo(NotificationType.DEADLINE);
			assertThat(notification.isRead()).isFalse();
			assertThat(notification.getCreatedAt()).isEqualTo(TestData.NOW);
		});
		ArgumentCaptor<DeadlineReminderMail> mail = ArgumentCaptor.forClass(DeadlineReminderMail.class);
		verify(mailer, times(1)).send(mail.capture());
		assertThat(mail.getValue())
			.isEqualTo(new DeadlineReminderMail(pending.getEmail(), "행사 참석 신청", deadline, "http://localhost:5173/f/r-" + formId));
		assertThat(notificationsOf(submitted)).isEmpty();
		assertThat(notificationsOf(optedOut)).isEmpty();
	}

	@Test
	void reminder_saved_within_window_마감_10시간_전에_저장한_폼도_다음_실행에서_1회() {
		Instant deadline = TestData.NOW.plus(Duration.ofHours(30));
		UUID formId = openForm(deadline);
		User late = user(true);

		clock.set(deadline.minus(Duration.ofHours(24)));
		reminders.run();
		assertThat(notificationsOf(late)).isEmpty();

		clock.set(deadline.minus(Duration.ofHours(10)));
		save(late, formId);
		reminders.run();
		clock.set(deadline.minus(Duration.ofHours(9)));
		reminders.run();

		assertThat(notificationsOf(late)).singleElement()
			.satisfies(notification -> assertThat(notification.getCreatedAt())
				.isEqualTo(deadline.minus(Duration.ofHours(10))));
		verifyMails(late, 1);
	}

	@Test
	void reminder_opt_out_알림을_끈_사용자는_알림도_메일도_없다() {
		UUID formId = openForm(TestData.NOW.plus(Duration.ofHours(5)));
		User optedOut = user(false);
		save(optedOut, formId);

		reminders.run();

		assertThat(notificationsOf(optedOut)).isEmpty();
		verifyMails(optedOut, 0);
	}

	@Test
	void reminder_deadline_없는_폼과_closed_폼과_구간_밖_마감은_발송하지_않는다() {
		User user = user(true);
		UUID noDeadline = openForm(null);
		UUID limitClosed = openForm(TestData.NOW.plus(Duration.ofHours(3)));
		Form closedForm = forms.findById(limitClosed).orElseThrow();
		closedForm.closeByResponseLimit(TestData.NOW);
		forms.saveAndFlush(closedForm);
		UUID tooEarly = openForm(TestData.NOW.plus(Duration.ofHours(24)).plusSeconds(1));
		UUID passed = openForm(TestData.NOW);
		UUID draft = openForm(TestData.NOW.plus(Duration.ofHours(3)));
		Form draftForm = forms.findById(draft).orElseThrow();
		ReflectionTestUtils.setField(draftForm, "status", FormStatus.DRAFT);
		forms.saveAndFlush(draftForm);
		for (UUID formId : List.of(noDeadline, limitClosed, tooEarly, passed, draft)) {
			save(user, formId);
		}

		reminders.run();

		assertThat(notificationsOf(user)).isEmpty();
		verifyMails(user, 0);
	}

	@Test
	void reminder_마감_정확히_24시간_전은_대상이다() {
		UUID formId = openForm(TestData.NOW.plus(Duration.ofHours(24)));
		User user = user(true);
		save(user, formId);

		reminders.run();

		assertThat(notificationsOf(user)).hasSize(1);
		verifyMails(user, 1);
	}

	@Test
	void reminder_동시에_두_번_돌아도_알림_1건_메일_1통() throws Exception {
		UUID formId = openForm(TestData.NOW.plus(Duration.ofHours(12)));
		User user = user(true);
		save(user, formId);

		CyclicBarrier start = new CyclicBarrier(2);
		Callable<Integer> run = () -> {
			start.await(10, TimeUnit.SECONDS);
			return reminders.run();
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// 다른 시험이 남긴 대상이 섞일 수 있어 합계가 아니라 이 사용자의 알림·메일로 판정한다. get()은 예외를 드러낸다.
			for (Future<Integer> result : pool.invokeAll(List.of(run, run))) {
				result.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(notificationsOf(user)).hasSize(1);
		verifyMails(user, 1);
	}

	@Test
	void reminder_메일이_실패해도_알림은_남고_다시_보내지_않는다() {
		UUID formId = openForm(TestData.NOW.plus(Duration.ofHours(2)));
		User user = user(true);
		save(user, formId);
		doThrow(new MailSendException("인증 실패")).when(mailer).send(any());

		reminders.run();
		reminders.run();

		assertThat(notificationsOf(user)).hasSize(1);
		verifyMails(user, 1);
	}

	@Test
	void reminder_제출을_모두_취소해_saved로_돌아오면_다시_대상이다() {
		UUID formId = openForm(TestData.NOW.plus(Duration.ofHours(6)));
		User user = user(true);
		savedForms.saveAndFlush(SavedForm.submitted(user.getId(), formId, clock.instant()));
		reminders.run();
		verify(mailer, never()).send(any());

		SavedForm item = savedForms.findByUserIdOrderBySavedAtDescFormIdAsc(user.getId()).get(0);
		item.markSaved();
		savedForms.saveAndFlush(item);
		reminders.run();

		assertThat(notificationsOf(user)).hasSize(1);
		verifyMails(user, 1);
	}

}
