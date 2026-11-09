package com.opinio.passform.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opinio.passform.storage.FileStorage;

// S3 원본 삭제. DB 커밋이 끝난 뒤에만 지운다. 다운로드 가능 여부의 기준은 DB 값이다.
// 사람 결정(#63): 짧게 재시도하고 그래도 실패하면 폼 ID와 개수만 경고 로그에 남긴다. 키는 남기지 않는다.
@Component
public class FileCleanup {

	private static final Logger log = LoggerFactory.getLogger(FileCleanup.class);

	private static final int ATTEMPTS = 3;

	private final FileStorage storage;

	private final Executor executor;

	public FileCleanup(FileStorage storage, @Qualifier("fileCleanupExecutor") Executor executor) {
		this.storage = storage;
		this.executor = executor;
	}

	// 커밋 콜백은 아직 DB 연결을 쥐고 있어서 별도 스레드에서 지운다. 롤백되면 아무것도 지우지 않는다.
	public void deleteAfterCommit(UUID formId, Collection<String> keys) {
		if (keys.isEmpty()) {
			return;
		}
		List<String> copy = List.copyOf(keys);
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("트랜잭션 안에서만 예약한다");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				executor.execute(() -> deleteAll(formId, copy));
			}
		});
	}

	// 올렸지만 최종 판정에 실패한 원본을 지운다. 호출자는 트랜잭션 밖이다.
	public void deleteNow(UUID formId, String key) {
		deleteAll(formId, List.of(key));
	}

	private void deleteAll(UUID formId, List<String> keys) {
		long failed = keys.stream().filter(key -> !deleted(key)).count();
		if (failed > 0) {
			log.warn("첨부 원본을 지우지 못했다: form_id={}, 개수={}", formId, failed);
		}
	}

	private boolean deleted(String key) {
		for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
			try {
				storage.delete(key);
				return true;
			}
			catch (RuntimeException exception) {
				log.debug("첨부 원본 삭제 재시도: {}회차", attempt);
			}
		}
		return false;
	}

}
