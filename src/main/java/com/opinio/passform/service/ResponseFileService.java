package com.opinio.passform.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.attachment.FileTypes;
import com.opinio.passform.domain.attachment.FileTypes.Audience;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.service.ResponseChangeService.EditCommand;
import com.opinio.passform.service.ResponseChangeService.EditPrecheck;
import com.opinio.passform.service.ResponseChangeService.EditedResponse;
import com.opinio.passform.storage.FileStorage;

// 응답 파일이 있는 제출·수정과 응답 첨부 다운로드(SPEC 5절 188·194·198행, 221~222행, AC64).
// 순서: 파일 없이 판정 → 형식·크기·용량 사전 검사 → S3 업로드 → 잠금 안 최종 판정. 어느 단계든 실패하면 올린 원본을 지운다.
// 여기에는 @Transactional을 두지 않는다. S3를 부르는 동안 DB 연결과 행 잠금을 쥐지 않기 위해서다.
@Service
public class ResponseFileService {

	private final SubmissionService submissions;

	private final ResponseChangeService changes;

	private final StorageQuotaService quota;

	private final AttachmentTransactions transactions;

	private final FileStorage storage;

	private final FileCleanup cleanup;

	public ResponseFileService(SubmissionService submissions, ResponseChangeService changes, StorageQuotaService quota,
			AttachmentTransactions transactions, FileStorage storage, FileCleanup cleanup) {
		this.submissions = submissions;
		this.changes = changes;
		this.quota = quota;
		this.transactions = transactions;
		this.storage = storage;
		this.cleanup = cleanup;
	}

	public SubmissionResult submit(SubmitCommand command, List<UploadedFile> files) {
		UUID ownerId = submissions.checkBeforeUpload(command, questionIdsOf(files));
		List<Inspected> inspected = inspect(files, Map.of(), 0, ownerId);
		return withUploaded(command.formId(), inspected, stored -> submissions.submit(command, stored));
	}

	public EditedResponse edit(EditCommand command, List<UploadedFile> files) {
		EditPrecheck precheck = changes.checkBeforeUpload(command, questionIdsOf(files));
		List<Inspected> inspected = inspect(files, precheck.keptBytesByQuestion(), precheck.freedBytes(),
				precheck.ownerId());
		return withUploaded(precheck.formId(), inspected, stored -> changes.edit(new EditCommand(command.userId(),
				command.responseId(), command.answers(), command.keepAttachmentIds(), stored)));
	}

	public URI downloadUrl(UUID userId, String formId, String responseId, String attachmentId) {
		Attachment attachment = transactions.readableResponseFile(userId, formId, responseId, attachmentId);
		return storage.presignDownload(attachment.getS3Key(), attachment.getContentType(), attachment.getFilename(),
				FileTypes.opensInline(attachment.getContentType()));
	}

	private List<Inspected> inspect(List<UploadedFile> files, Map<String, Long> keptBytes, long freedBytes,
			UUID ownerId) {
		List<Inspected> inspected = new ArrayList<>();
		Map<String, Long> bytesByQuestion = new HashMap<>(keptBytes);
		long total = 0;
		for (UploadedFile file : files) {
			String filename = FileTypes.cleanFilename(file.filename());
			inspected.add(new Inspected(file, filename, FileTypes.detect(filename, headOf(file.content()), Audience.RESPONDENT)));
			bytesByQuestion.merge(file.questionId(), file.sizeBytes(), Long::sum);
			total += file.sizeBytes();
		}
		ResponseFileLimits.requireWithinQuestionLimit(bytesByQuestion);
		// 최종 판정은 잠금 안에서 다시 한다. 여기서는 교체로 빠지는 파일을 빼고 본다.
		if (total > 0 && quota.usedBytes(ownerId) - freedBytes + total > StorageQuotaService.LIMIT_BYTES) {
			throw new DomainException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
		}
		return inspected;
	}

	private interface Save<T> {

		T apply(List<StoredFile> stored);

	}

	private <T> T withUploaded(UUID formId, List<Inspected> inspected, Save<T> save) {
		List<StoredFile> stored = new ArrayList<>();
		try {
			for (Inspected file : inspected) {
				// 키는 서버가 새로 만들고 파일 이름을 넣지 않는다.
				String key = "attachments/" + formId + "/" + UUID.randomUUID();
				// S3가 저장한 뒤 응답이 끊겨 put이 실패할 수 있어 먼저 목록에 넣는다.
				stored.add(new StoredFile(file.upload().questionId(), file.filename(), file.contentType(),
						file.upload().sizeBytes(), key));
				storage.put(key, file.upload().content(), file.upload().sizeBytes(), file.contentType());
			}
			return save.apply(stored);
		}
		catch (RuntimeException exception) {
			// put 실패와 커밋 실패까지 여기로 온다. 실패한 요청의 원본을 남기지 않는다.
			stored.forEach(file -> cleanup.deleteNow(formId, file.key()));
			throw exception;
		}
	}

	private static Set<String> questionIdsOf(List<UploadedFile> files) {
		return files.stream().map(UploadedFile::questionId).collect(Collectors.toSet());
	}

	private static byte[] headOf(InputStreamSource content) {
		try (InputStream stream = content.getInputStream()) {
			return stream.readNBytes(FileTypes.HEAD_BYTES);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private record Inspected(UploadedFile upload, String filename, String contentType) {
	}

	public record UploadedFile(String questionId, String filename, long sizeBytes, InputStreamSource content) {
	}

}
