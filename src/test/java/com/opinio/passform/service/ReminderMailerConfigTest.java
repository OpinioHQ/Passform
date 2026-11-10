package com.opinio.passform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.opinio.passform.mail.ReminderMailer;
import com.opinio.passform.mail.SmtpReminderMailer;
import com.opinio.passform.mail.UnconfiguredReminderMailer;

// MAIL_USERNAME이나 MAIL_PASSWORD가 비면 메일 없이 사이트 알림만 만든다(사람 결정, #57). 연결은 만들지 않는다.
class ReminderMailerConfigTest {

	private final ReminderMailerConfig config = new ReminderMailerConfig();

	@Test
	void 사용자_이름이나_앱_비밀번호가_비면_보내지_않는_구현이다() {
		assertThat(config.reminderMailer("smtp.gmail.com", 587, "", "secret", "", 24))
			.isInstanceOf(UnconfiguredReminderMailer.class);
		assertThat(config.reminderMailer("smtp.gmail.com", 587, "sender@example.com", " ", "", 24))
			.isInstanceOf(UnconfiguredReminderMailer.class);
	}

	@Test
	void 둘_다_있으면_STARTTLS_SMTP로_보내고_보낸_사람이_없으면_사용자_이름을_쓴다() {
		ReminderMailer mailer = config.reminderMailer("smtp.gmail.com", 587, "sender@example.com", "app-password", "",
				24);

		assertThat(mailer).isInstanceOf(SmtpReminderMailer.class);
		assertThat(ReflectionTestUtils.getField(mailer, "from")).isEqualTo("sender@example.com");
		JavaMailSenderImpl sender = (JavaMailSenderImpl) ReflectionTestUtils.getField(mailer, "sender");
		assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
		assertThat(sender.getPort()).isEqualTo(587);
		assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
			.containsEntry("mail.smtp.starttls.enable", "true")
			.containsEntry("mail.smtp.starttls.required", "true");

		ReminderMailer named = config.reminderMailer("smtp.gmail.com", 587, "sender@example.com", "app-password",
				"Passform <sender@example.com>", 24);
		assertThat(ReflectionTestUtils.getField(named, "from")).isEqualTo("Passform <sender@example.com>");
	}

}
