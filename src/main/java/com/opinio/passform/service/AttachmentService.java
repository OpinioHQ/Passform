package com.opinio.passform.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.attachment.FileTypes;
import com.opinio.passform.domain.attachment.FileTypes.Audience;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.storage.FileStorage;

// 폼 설명란·문항 첨부(SPEC 5절 121~124행, 169~170행, 196~197행, AC63).
// 여기에는 @Transactional을 두지 않는다. S3를 부르는 동안 DB 연결과 폼·사용자 행 잠금을 쥐지 않기 위해서다.
@Service
public class AttachmentService {

	private final AttachmentTransactions transactions;

	private final FileStorage storage;

	private final FileCleanup cleanup;

	public AttachmentService(AttachmentTransactions transactions, FileStorage storage, FileCleanup cleanup) {
		this.transactions = transactions;
		this.storage = storage;
		this.cleanup = cleanup;
	}

	public UploadedAttachment add(AddCommand command) {
		UUID formId = FormService.parseId(command.formId());
		long used = transactions.checkBeforeUpload(command);
		String filename = FileTypes.cleanFilename(command.filename());
		String contentType = FileTypes.detect(filename, headOf(command.content()), Audience.CREATOR);
		if (used + command.sizeBytes() > StorageQuotaService.LIMIT_BYTES) {
			throw new DomainException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
		}

		// 키는 서버가 새로 만들고 파일 이름을 넣지 않는다.
		String key = "attachments/" + formId + "/" + UUID.randomUUID();
		storage.put(key, command.content(), command.sizeBytes(), contentType);
		try {
			return transactions.saveUploaded(command, filename, contentType, key);
		}
		catch (RuntimeException exception) {
			// 커밋 실패까지 여기로 온다. 실패한 요청의 원본을 남기지 않는다.
			cleanup.deleteNow(formId, key);
			throw exception;
		}
	}

	public void remove(RemoveCommand command) {
		transactions.remove(command);
	}

	// 권한을 확인한 뒤에만 Presigned URL을 만든다.
	public URI downloadUrl(DownloadCommand command) {
		Attachment attachment = transactions.readableAttachment(command);
		return storage.presignDownload(attachment.getS3Key(), attachment.getContentType(), attachment.getFilename(),
				FileTypes.opensInline(attachment.getContentType()));
	}

	private static byte[] headOf(InputStreamSource content) {
		try (InputStream stream = content.getInputStream()) {
			return stream.readNBytes(FileTypes.HEAD_BYTES);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	public record AddCommand(UUID userId, String formId, String questionId, String filename, long sizeBytes,
			InputStreamSource content) {
	}

	public record RemoveCommand(UUID userId, String formId, String questionId, String attachmentId) {
	}

	public record DownloadCommand(Optional<UUID> userId, String formId, String questionId, String attachmentId) {
	}

	public record UploadedAttachment(UUID attachmentId, String filename, String contentType, long sizeBytes) {
	}

}
