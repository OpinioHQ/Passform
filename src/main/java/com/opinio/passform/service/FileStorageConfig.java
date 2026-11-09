package com.opinio.passform.service;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import com.opinio.passform.storage.FileStorage;
import com.opinio.passform.storage.S3FileStorage;
import com.opinio.passform.storage.UnconfiguredFileStorage;

// service 패키지에 둔다. 권한 골든 테스트가 이 패키지만 스캔해 서비스를 조립하기 때문이다.
@Configuration(proxyBeanMethods = false)
public class FileStorageConfig {

	// 버킷이 없으면 S3 클라이언트를 만들지 않는다. 자격 증명 없이도 앱이 뜬다.
	@Bean
	FileStorage fileStorage(@Value("${passform.s3.bucket:}") String bucket,
			@Value("${passform.s3.region:ap-northeast-2}") String region) {
		return StringUtils.hasText(bucket) ? new S3FileStorage(bucket, region) : new UnconfiguredFileStorage();
	}

	// 커밋 뒤 원본 삭제를 요청 처리와 DB 연결 밖에서 한다.
	@Bean
	Executor fileCleanupExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setThreadNamePrefix("file-cleanup-");
		executor.setDaemon(true);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(20);
		return executor;
	}

}
