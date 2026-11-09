package com.opinio.passform.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.repository.FormRepository;

// 공유 링크 QR(SPEC 5절 126행, AC10). 제작자만 받는다(AC2).
@Service
public class FormQrService {

	private final FormRepository forms;

	private final ShareUrls shareUrls;

	private final QrCodes qrCodes;

	public FormQrService(FormRepository forms, ShareUrls shareUrls, QrCodes qrCodes) {
		this.forms = forms;
		this.shareUrls = shareUrls;
		this.qrCodes = qrCodes;
	}

	// 한 번도 open하지 않아 share_url이 없으면 not_open이다. open 뒤 draft로 되돌린 폼은 링크가 남아 있어 만든다.
	@Transactional(readOnly = true)
	public byte[] png(UUID userId, String formId) {
		Form form = forms.findById(FormService.parseId(formId)).orElseThrow(FormService::notFound);
		FormService.requireOwner(form, userId);
		String shareUrl = shareUrls.of(form.getSlug());
		if (shareUrl == null) {
			throw new DomainException(ErrorCode.NOT_OPEN);
		}
		return qrCodes.png(shareUrl);
	}

}
