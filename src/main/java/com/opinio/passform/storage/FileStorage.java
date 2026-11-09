package com.opinio.passform.storage;

import java.net.URI;

import org.springframework.core.io.InputStreamSource;

// 첨부 원본 저장소. 외부 호출은 이 인터페이스 뒤에 두고 테스트에서 메모리 저장소로 바꾼다(AGENTS 5절).
// 실패는 DomainException(STORAGE_UNAVAILABLE)으로 던진다. 파일 이름·키·URL을 로그와 예외에 넣지 않는다.
public interface FileStorage {

	// 재시도할 수 있게 내용을 다시 열 수 있는 원천으로 받는다.
	void put(String key, InputStreamSource content, long sizeBytes, String contentType);

	void delete(String key);

	// inline이 false면 브라우저가 바로 열지 않고 내려받는다(SPEC 5절 220행).
	URI presignDownload(String key, String contentType, String filename, boolean inline);

}
