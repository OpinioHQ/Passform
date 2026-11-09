package com.opinio.passform.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.attachment.Attachment;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.FormAttachments;
import com.opinio.passform.domain.form.FormVersion;
import com.opinio.passform.repository.AttachmentRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.UserRepository;

// 계정별 500MB 사용량(SPEC 5절 171~172행, AC65).
// 동시 저장이 함께 한도를 넘지 않게 소유자 users 행으로 직렬화한다. 잠금 순서는 항상 폼 행 → 소유자 행이다.
@Service
public class StorageQuotaService implements FormStorageRules {

	public static final long LIMIT_BYTES = 500_000_000L;

	private final UserRepository users;

	private final AttachmentRepository attachments;

	private final FormVersionRepository versions;

	private final FileCleanup cleanup;

	public StorageQuotaService(UserRepository users, AttachmentRepository attachments, FormVersionRepository versions,
			FileCleanup cleanup) {
		this.users = users;
		this.attachments = attachments;
		this.versions = versions;
		this.cleanup = cleanup;
	}

	@Transactional(readOnly = true)
	public long usedBytes(UUID userId) {
		return attachments.usedBytes(userId);
	}

	@Override
	@Transactional
	public long lockAndMeasure(UUID ownerId) {
		users.findByIdForUpdate(ownerId).orElseThrow(() -> new IllegalStateException("폼 소유자가 없다"));
		return attachments.usedBytes(ownerId);
	}

	@Override
	@Transactional
	public void requireWithinLimit(UUID ownerId, long usedBefore) {
		attachments.flush();
		long usedAfter = attachments.usedBytes(ownerId);
		// 이미 넘은 계정도 줄이는 저장은 할 수 있다.
		if (usedAfter > LIMIT_BYTES && usedAfter > usedBefore) {
			throw new DomainException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
		}
	}

	@Override
	@Transactional
	public void releaseUnreferenced(UUID formId, Set<String> attachmentIds) {
		if (attachmentIds.isEmpty()) {
			return;
		}
		Set<String> stillUsed = versions.findByFormId(formId)
			.stream()
			.map(FormVersion::getDefinition)
			.flatMap(definition -> FormAttachments.allIds(definition).stream())
			.collect(Collectors.toSet());
		List<UUID> gone = attachmentIds.stream()
			.filter(id -> !stillUsed.contains(id))
			.map(StorageQuotaService::parseOrNull)
			.filter(Objects::nonNull)
			.toList();
		List<String> keys = new ArrayList<>();
		for (Attachment attachment : attachments.findAllById(gone)) {
			if (attachment.getFormId().equals(formId) && attachment.getResponseId() == null) {
				keys.add(attachment.getS3Key());
				attachments.delete(attachment);
			}
		}
		cleanup.deleteAfterCommit(formId, keys);
	}

	// 첨부 API가 만든 ID는 UUID다. 그 밖의 값에는 지울 행이 없다.
	private static UUID parseOrNull(String id) {
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

}
