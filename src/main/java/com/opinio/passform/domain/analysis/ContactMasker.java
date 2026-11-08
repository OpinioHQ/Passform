package com.opinio.passform.domain.analysis;

import java.util.regex.Pattern;

// 외부로 보내는 단답·장문 답 속 전화번호·이메일을 가린다(SPEC 332행, AC92). 하이픈·공백·점 유무와 관계없이 가린다.
public final class ContactMasker {

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");

	// 휴대전화 01X, 국가번호 +82, 지역번호 02·0NN 전화(9~11자리)다.
	private static final Pattern PHONE = Pattern
		.compile("(?<![0-9])(?:(?:\\+?82[-.\\s]?)1[016789]|01[016789]|0[2-9][0-9]?)[-.\\s]?[0-9]{3,4}[-.\\s]?[0-9]{4}(?![0-9])");

	private ContactMasker() {
	}

	public static String mask(String text) {
		String withoutEmail = EMAIL.matcher(text).replaceAll("[이메일]");
		return PHONE.matcher(withoutEmail).replaceAll("[전화번호]");
	}

}
