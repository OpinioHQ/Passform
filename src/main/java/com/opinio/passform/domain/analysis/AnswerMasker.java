package com.opinio.passform.domain.analysis;

import java.util.regex.Pattern;

// 외부로 보내는 답 속 전화번호·이메일을 가린다(AC92).
public final class AnswerMasker {

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final Pattern PHONE = Pattern
		.compile("(?<!\\d)(\\+?82[-.\\s]?1\\d|0\\d{1,2})[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");

	private AnswerMasker() {
	}

	public static String mask(String text) {
		String withoutEmail = EMAIL.matcher(text).replaceAll("[이메일]");
		return PHONE.matcher(withoutEmail).replaceAll("[전화번호]");
	}

}
