package com.opinio.passform.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;
import com.opinio.passform.domain.form.FormVersionId;
import com.opinio.passform.domain.inbox.NotificationType;
import com.opinio.passform.domain.inbox.SavedFormState;
import com.opinio.passform.domain.user.User;
import com.opinio.passform.mail.DeadlineReminderMail;
import com.opinio.passform.mail.ReminderMailer;
import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.NotificationRepository;
import com.opinio.passform.repository.SavedFormRepository;
import com.opinio.passform.repository.SavedFormRepository.ReminderTarget;
import com.opinio.passform.repository.UserRepository;

// 마감 알림(AC18). 스케줄러가 부르며, 같은 시점에 여러 번 돌아도 사용자·폼·종류마다 알림 1건·메일 1통이다.
// 트랜잭션을 두지 않는다: 알림 행은 한 건씩 자기 트랜잭션으로 커밋하고, 커밋된 뒤에만 메일을 보낸다.
@Service
public class DeadlineReminderService {

	private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);

	private final SavedFormRepository savedForms;

	private final NotificationRepository notifications;

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final UserRepository users;

	private final ShareUrls shareUrls;

	private final ReminderMailer mailer;

	private final Clock clock;

	private final Duration window;

	public DeadlineReminderService(SavedFormRepository savedForms, NotificationRepository notifications,
			FormRepository forms, FormVersionRepository versions, UserRepository users, ShareUrls shareUrls,
			ReminderMailer mailer, Clock clock, @Value("${passform.deadline-reminder.hours:24}") long hours) {
		this.savedForms = savedForms;
		this.notifications = notifications;
		this.forms = forms;
		this.versions = versions;
		this.users = users;
		this.shareUrls = shareUrls;
		this.mailer = mailer;
		this.clock = clock;
		this.window = Duration.ofHours(hours);
	}

	// 한 번 실행하고 새로 만든 알림 수를 돌려준다.
	public int run() {
		Instant now = clock.instant();
		int created = 0;
		for (ReminderTarget target : savedForms.findReminderTargets(now, now.plus(window), SavedFormState.SAVED,
				FormStatus.OPEN, NotificationType.DEADLINE)) {
			if (remind(target.getUserId(), target.getFormId(), now)) {
				created++;
			}
		}
		return created;
	}

	private boolean remind(UUID userId, UUID formId, Instant now) {
		Optional<Form> form = forms.findById(formId).filter(found -> found.isDeadlineReminderDue(now, window));
		Optional<User> user = users.findById(userId).filter(User::isDeadlineReminder);
		if (form.isEmpty() || user.isEmpty()) {
			return false;
		}
		// 0이면 다른 실행이 먼저 만들었다. 메일도 그쪽이 보낸다.
		if (notifications.insertIfAbsent(UUID.randomUUID(), userId, formId, NotificationType.DEADLINE.name(),
				now) == 0) {
			return false;
		}
		send(user.get(), form.get());
		return true;
	}

	// 실패해도 알림 행은 남기고 다시 보내지 않는다(중복 메일 방지가 우선). 받는 주소와 예외 메시지는 로그에 쓰지 않는다.
	private void send(User user, Form form) {
		try {
			String title = versions.findById(new FormVersionId(form.getId(), form.getDefinitionVersion()))
				.map(version -> InboxService.titleOf(version.getDefinition()))
				.orElse("");
			mailer.send(new DeadlineReminderMail(user.getEmail(), title, form.getDeadline(),
					shareUrls.of(form.getSlug())));
		}
		catch (RuntimeException e) {
			log.warn("deadline_reminder_mail_failed form_id={} cause={}", form.getId(), e.getClass().getSimpleName());
		}
	}

}
