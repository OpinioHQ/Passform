package com.opinio.passform.mail;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

// 마감 알림 메일의 제목과 본문(일반 텍스트). 표시는 Asia/Seoul이다(AGENTS.md 5절).
public final class ReminderMailText {

	private static final DateTimeFormatter SEOUL = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREAN)
		.withZone(ZoneId.of("Asia/Seoul"));

	// 폼 제목은 사용자가 쓴 값이다. 줄바꿈 등 제어 문자를 지워 메일 헤더 주입을 막는다.
	private static final Pattern LINE_BREAKS = Pattern.compile("[\\p{Cntrl}\\u0085\\u2028\\u2029]+");

	private ReminderMailText() {
	}

	public static String subject(DeadlineReminderMail mail, long hours) {
		return "[Passform] 마감 " + hours + "시간 전: " + oneLine(mail.formTitle());
	}

	public static String body(DeadlineReminderMail mail) {
		return oneLine(mail.formTitle()) + "\n" + "마감: " + SEOUL.format(mail.deadline()) + " (한국 시각)\n" + "폼 열기: "
				+ mail.formUrl() + "\n\n" + "알림은 Passform 화면 오른쪽 위, 사용자명을 눌러 끌 수 있어요.\n";
	}

	static String oneLine(String text) {
		return text == null ? "" : LINE_BREAKS.matcher(text).replaceAll(" ").strip();
	}

}
