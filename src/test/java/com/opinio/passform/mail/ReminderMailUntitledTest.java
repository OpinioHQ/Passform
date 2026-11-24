package com.opinio.passform.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// 제목이 빈 폼의 마감 알림 메일은 화면과 같이 "제목 없는 폼"으로 보인다(사람 결정, #165). 저장값은 그대로다.
class ReminderMailUntitledTest {

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "\n", "\r\n", " " })
	void 빈_제목은_메일_제목과_본문_첫_줄에_제목_없는_폼으로_보인다(String title) {
		DeadlineReminderMail mail = new DeadlineReminderMail("virtual@example.com", title,
				Instant.parse("2026-11-12T05:00:00Z"), "https://app.example.com/f/abc");

		assertThat(ReminderMailText.subject(mail, 24)).isEqualTo("[Passform] 마감 24시간 전: 제목 없는 폼");
		assertThat(ReminderMailText.body(mail).lines().findFirst()).hasValue("제목 없는 폼");
	}

}
