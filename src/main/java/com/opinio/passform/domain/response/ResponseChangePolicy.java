package com.opinio.passform.domain.response;

import java.time.Instant;
import java.util.Optional;

import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.form.Form;
import com.opinio.passform.domain.form.FormStatus;

// 제출 후 수정·취소의 판정 한 곳(SPEC 5절 226행, AC16). 수정·취소 API와 이력의 editable·cancellable이 함께 쓴다.
// 운영 설정과 마감은 폼의 현재 값으로 본다(SPEC 5절 159행). 순서: history_only → form_closed → not_editable·not_cancellable.
public final class ResponseChangePolicy {

	public enum Change {

		EDIT, CANCEL

	}

	private ResponseChangePolicy() {
	}

	// 막는 사유를 돌려준다. 비어 있으면 허용이다. form이 null이면 폼이 삭제되어 이력만 남은 응답이다.
	public static Optional<ErrorCode> blockedBy(Change change, Form form, Response response, Instant now) {
		if (form == null || form.isDeleted() || response.getCreatorDeletedAt() != null) {
			return Optional.of(ErrorCode.HISTORY_ONLY);
		}
		if (form.getStatus() != FormStatus.OPEN || form.isClosedAt(now)) {
			return Optional.of(ErrorCode.FORM_CLOSED);
		}
		if (change == Change.EDIT && !form.isEditable()) {
			return Optional.of(ErrorCode.NOT_EDITABLE);
		}
		if (change == Change.CANCEL && !form.isCancellable()) {
			return Optional.of(ErrorCode.NOT_CANCELLABLE);
		}
		return Optional.empty();
	}

	public static boolean allowed(Change change, Form form, Response response, Instant now) {
		return blockedBy(change, form, response, now).isEmpty();
	}

}
