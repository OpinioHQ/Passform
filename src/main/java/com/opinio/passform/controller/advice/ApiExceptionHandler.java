package com.opinio.passform.controller.advice;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// 오류 응답은 SPEC 5절의 {error: "코드", ...} 형태다. 오류 코드-HTTP 상태 매핑은 이 클래스에만 둔다.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	// default를 두지 않아 코드를 더하고 매핑을 빠뜨리면 컴파일이 실패한다.
	static HttpStatus statusOf(ErrorCode code) {
		return switch (code) {
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case FORBIDDEN -> HttpStatus.FORBIDDEN;
			case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
			case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
			case SCHEMA_INVALID, INVALID_PAYMENT_LINK, INVALID_BRANCH, INVALID_DISPLAY_MODE, CONSENT_TEMPLATE_REQUIRED,
					AI_SUMMARY_LOCKED -> HttpStatus.BAD_REQUEST;
			case INVALID_TRANSITION, RESPONSE_LIMIT_REACHED, FORM_CLOSED -> HttpStatus.CONFLICT;
		};
	}

	@ExceptionHandler(DomainException.class)
	ResponseEntity<Object> handleDomain(DomainException exception) {
		ErrorCode code = exception.getErrorCode();
		return ResponseEntity.status(statusOf(code))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body(code, exception.getDetails()));
	}

	// 내부 내용은 로그에만 남기고 응답에는 코드만 보낸다.
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception exception) throws Exception {
		// 보안 필터가 401·403으로 처리해야 하므로 500으로 바꾸지 않는다.
		if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
			throw exception;
		}
		log.error("처리하지 못한 예외", exception);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(statusOf(code))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body(code, Map.of()));
	}

	// 프레임워크 예외(형식 오류, 없는 경로, 허용하지 않는 메서드 등)는 상태를 유지하고 본문만 맞춘다.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object ignoredBody,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ErrorCode code = frameworkCode(status);
		if (code == ErrorCode.INTERNAL_ERROR) {
			log.error("처리하지 못한 프레임워크 예외", exception);
		}
		return ResponseEntity.status(status)
			.headers(headers)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body(code, Map.of()));
	}

	private static ErrorCode frameworkCode(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return ErrorCode.INTERNAL_ERROR;
		}
		return status.isSameCodeAs(HttpStatus.NOT_FOUND) ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_REQUEST;
	}

	private static Map<String, Object> body(ErrorCode code, Map<String, Object> details) {
		Map<String, Object> body = new LinkedHashMap<>(details);
		body.put("error", code.code());
		return body;
	}

}
