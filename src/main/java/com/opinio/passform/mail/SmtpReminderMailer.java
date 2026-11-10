package com.opinio.passform.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

// 운영 구현. Gmail SMTP(STARTTLS, 앱 비밀번호)로 일반 텍스트 메일을 보낸다.
public class SmtpReminderMailer implements ReminderMailer {

	private final JavaMailSender sender;

	private final String from;

	private final long hours;

	public SmtpReminderMailer(JavaMailSender sender, String from, long hours) {
		this.sender = sender;
		this.from = from;
		this.hours = hours;
	}

	@Override
	public void send(DeadlineReminderMail mail) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(mail.to());
		message.setSubject(ReminderMailText.subject(mail, hours));
		message.setText(ReminderMailText.body(mail));
		sender.send(message);
	}

}
