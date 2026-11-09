package com.opinio.passform.storage;

import java.net.URI;

import org.springframework.core.io.InputStreamSource;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// S3 설정이 없을 때 쓴다. 앱은 뜨고 파일 기능만 503이다(AWS 설정 없이 빌드·테스트·시작이 되어야 한다).
public class UnconfiguredFileStorage implements FileStorage {

	@Override
	public void put(String key, InputStreamSource content, long sizeBytes, String contentType) {
		throw unavailable();
	}

	@Override
	public void delete(String key) {
		throw unavailable();
	}

	@Override
	public void copy(String sourceKey, String targetKey) {
		throw unavailable();
	}

	@Override
	public URI presignDownload(String key, String contentType, String filename, boolean inline) {
		throw unavailable();
	}

	private static DomainException unavailable() {
		return new DomainException(ErrorCode.STORAGE_UNAVAILABLE);
	}

}
