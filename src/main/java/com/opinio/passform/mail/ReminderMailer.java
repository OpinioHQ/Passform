package com.opinio.passform.mail;

// 마감 알림 메일 발송(AC18). 테스트에서는 모의 객체로 바꾸고 실제 SMTP를 부르지 않는다(AGENTS.md 5절).
// 넘기는 값은 받는 주소, 폼 제목, 마감 시각, 폼 링크뿐이다.
public interface ReminderMailer {

	// 실패하면 예외를 던진다. 다시 보내지 않는 판단은 부르는 쪽이 한다.
	void send(DeadlineReminderMail mail);

}
