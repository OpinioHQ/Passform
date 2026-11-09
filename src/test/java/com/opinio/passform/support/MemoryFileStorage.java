package com.opinio.passform.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.InputStreamSource;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.storage.FileStorage;

// 테스트와 CI에서 실제 S3 대신 쓰는 저장소. 객체 수와 호출 수로 "실패하면 아무것도 남지 않는다"를 단언한다.
public class MemoryFileStorage implements FileStorage {

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		MemoryFileStorage memoryFileStorage() {
			return new MemoryFileStorage();
		}

	}

	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	private final AtomicInteger puts = new AtomicInteger();

	private final AtomicInteger deletes = new AtomicInteger();

	private volatile boolean failPuts;

	private volatile CyclicBarrier putBarrier;

	public void reset() {
		objects.clear();
		puts.set(0);
		deletes.set(0);
		failPuts = false;
		putBarrier = null;
	}

	public void failPuts() {
		failPuts = true;
	}

	// 동시 요청이 모두 사전 검사를 지나 업로드 단계에 도달할 때까지 기다리게 한다.
	public void holdPutsUntil(int parties) {
		putBarrier = new CyclicBarrier(parties);
	}

	public int objectCount() {
		return objects.size();
	}

	public boolean has(String key) {
		return objects.containsKey(key);
	}

	public java.util.Set<String> keys() {
		return java.util.Set.copyOf(objects.keySet());
	}

	public int putCount() {
		return puts.get();
	}

	public int deleteCount() {
		return deletes.get();
	}

	// 커밋 뒤 삭제는 다른 스레드에서 돈다.
	public void awaitObjectCount(int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (objects.size() != expected && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
	}

	@Override
	public void put(String key, InputStreamSource content, long sizeBytes, String contentType) {
		puts.incrementAndGet();
		CyclicBarrier barrier = putBarrier;
		if (barrier != null) {
			try {
				barrier.await(10, TimeUnit.SECONDS);
			}
			catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		}
		if (failPuts) {
			throw new DomainException(ErrorCode.STORAGE_UNAVAILABLE);
		}
		try (InputStream stream = content.getInputStream()) {
			objects.put(key, stream.readAllBytes());
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Override
	public void delete(String key) {
		deletes.incrementAndGet();
		objects.remove(key);
	}

	@Override
	public void copy(String sourceKey, String targetKey) {
		byte[] source = objects.get(sourceKey);
		if (failPuts || source == null) {
			throw new DomainException(ErrorCode.STORAGE_UNAVAILABLE);
		}
		objects.put(targetKey, source);
	}

	@Override
	public URI presignDownload(String key, String contentType, String filename, boolean inline) {
		return URI.create("https://memory.test/" + key + "?type=" + URLEncoder.encode(contentType, StandardCharsets.UTF_8)
				+ "&inline=" + inline);
	}

}
