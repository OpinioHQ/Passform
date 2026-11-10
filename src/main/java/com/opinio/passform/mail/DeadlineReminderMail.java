package com.opinio.passform.mail;

import java.time.Instant;

// to는 User.email(알림 수신 주소)이다. Profile의 email이 아니다.
public record DeadlineReminderMail(String to, String formTitle, Instant deadline, String formUrl) {
}
