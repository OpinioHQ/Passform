package com.opinio.passform.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.ContentDisposition;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

// 자격 증명은 AWS 기본 경로(OS 환경변수 또는 서버 역할)로만 얻는다. 버킷은 공개하지 않는다.
public class S3FileStorage implements FileStorage {

	private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

	// 사람 결정(#63): 다운로드 주소는 5분만 유효하다.
	private static final Duration DOWNLOAD_VALIDITY = Duration.ofMinutes(5);

	private final String bucket;

	private final S3Client client;

	private final S3Presigner presigner;

	public S3FileStorage(String bucket, String region) {
		this.bucket = bucket;
		this.client = S3Client.builder().region(Region.of(region)).build();
		this.presigner = S3Presigner.builder().region(Region.of(region)).build();
	}

	@Override
	public void put(String key, InputStreamSource content, long sizeBytes, String contentType) {
		PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
		try {
			client.putObject(request, RequestBody.fromContentProvider(() -> open(content), sizeBytes, contentType));
		}
		catch (SdkException | UncheckedIOException exception) {
			throw unavailable("저장", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		}
		catch (SdkException exception) {
			throw unavailable("삭제", exception);
		}
	}

	@Override
	public void copy(String sourceKey, String targetKey) {
		try {
			client.copyObject(CopyObjectRequest.builder()
				.sourceBucket(bucket)
				.sourceKey(sourceKey)
				.destinationBucket(bucket)
				.destinationKey(targetKey)
				.build());
		}
		catch (SdkException exception) {
			throw unavailable("복사", exception);
		}
	}

	@Override
	public URI presignDownload(String key, String contentType, String filename, boolean inline) {
		ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
			.filename(filename, StandardCharsets.UTF_8)
			.build();
		GetObjectRequest object = GetObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.responseContentType(contentType)
			.responseContentDisposition(disposition.toString())
			.build();
		try {
			return presigner
				.presignGetObject(GetObjectPresignRequest.builder()
					.signatureDuration(DOWNLOAD_VALIDITY)
					.getObjectRequest(object)
					.build())
				.url()
				.toURI();
		}
		catch (SdkException | java.net.URISyntaxException exception) {
			throw unavailable("주소 생성", exception);
		}
	}

	private static java.io.InputStream open(InputStreamSource content) {
		try {
			return content.getInputStream();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	// 예외 메시지에 버킷·키가 들어갈 수 있어 종류만 남긴다.
	private static DomainException unavailable(String action, Exception exception) {
		log.warn("S3 {} 실패: {}", action, exception.getClass().getSimpleName());
		return new DomainException(ErrorCode.STORAGE_UNAVAILABLE);
	}

}
