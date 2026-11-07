package com.opinio.passform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// share_url은 프런트 주소 뒤에 /f/{slug}를 붙인다(SPEC 5절 204행의 <도메인>을 프런트 주소로 읽는다. 사람 결정, 이슈 #40).
@Component
public class ShareUrls {

	private final String frontendOrigin;

	public ShareUrls(@Value("${passform.frontend-origin}") String frontendOrigin) {
		this.frontendOrigin = frontendOrigin.endsWith("/")
				? frontendOrigin.substring(0, frontendOrigin.length() - 1)
				: frontendOrigin;
	}

	// 처음 open하기 전에는 slug가 없어 null이다.
	public String of(String slug) {
		return slug == null ? null : frontendOrigin + "/f/" + slug;
	}

}
