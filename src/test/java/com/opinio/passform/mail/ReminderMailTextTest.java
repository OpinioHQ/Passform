package com.opinio.passform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

// 마감 알림 메일의 제목·본문(사람 결정, #57). 실제 SMTP는 부르지 않는다.
class ReminderMailTextTest {

	private static final DeadlineReminderMail MAIL = new DeadlineReminderMail("virtual@example.com", "행사 참석 신청",
			Instant.parse("2026-11-12T05:00:00Z"), "https://app.example.com/f/abc");

	@Test
	void 제목과_본문은_폼_제목_한국_마감_시각_링크_끄는_곳을_담은_일반_텍스트다() {
		assertThat(ReminderMailText.subject(MAIL, 24)).isEqualTo("[Passform] 마감 24시간 전: 행사 참석 신청");
		assertThat(ReminderMailText.body(MAIL)).isEqualTo("""
				행사 참석 신청
				마감: 2026년 11월 12일 14:00 (한국 시각)
				폼 열기: https://app.example.com/f/abc

				알림은 Passform 화면 오른쪽 위, 사용자명을 눌러 끌 수 있어요.
				""");
	}

	@Test
	void 폼_제목의_줄바꿈은_헤더_주입이_되지_않게_지운다() {
		DeadlineReminderMail injected = new DeadlineReminderMail("virtual@example.com",
				"제목\r\nBcc: attacker@example.com 끝", MAIL.deadline(), MAIL.formUrl());

		String subject = ReminderMailText.subject(injected, 24);

		assertThat(subject).doesNotContain("\r", "\n", " ")
			.isEqualTo("[Passform] 마감 24시간 전: 제목 Bcc: attacker@example.com 끝");
		assertThat(ReminderMailText.body(injected).lines().findFirst()).hasValue("제목 Bcc: attacker@example.com 끝");
		assertThat(ReminderMailText.oneLine(null)).isEmpty();
	}

	@Test
	void SMTP_구현은_보낸_사람과_받는_주소_제목_본문을_채워_보낸다() {
		JavaMailSender sender = mock(JavaMailSender.class);

		new SmtpReminderMailer(sender, "Passform <sender@example.com>", 24).send(MAIL);

		ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(sender).send(message.capture());
		assertThat(message.getValue().getFrom()).isEqualTo("Passform <sender@example.com>");
		assertThat(message.getValue().getTo()).containsExactly("virtual@example.com");
		assertThat(message.getValue().getSubject()).isEqualTo("[Passform] 마감 24시간 전: 행사 참석 신청");
		assertThat(message.getValue().getText()).isEqualTo(ReminderMailText.body(MAIL));
	}

}
