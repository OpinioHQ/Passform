package com.opinio.passform.mail;

// 메일 설정(MAIL_USERNAME, MAIL_PASSWORD)이 없을 때 쓴다. 메일은 보내지 않고 사이트 알림만 남는다.
// 로컬 개발·CI·테스트가 설정 없이 돌아야 한다.
public class UnconfiguredReminderMailer implements ReminderMailer {

	@Override
	public void send(DeadlineReminderMail mail) {
		// 보내지 않는다.
	}

}
