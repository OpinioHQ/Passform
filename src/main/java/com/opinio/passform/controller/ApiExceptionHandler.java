package com.opinio.passform.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.opinio.passform.domain.ConflictException;
import com.opinio.passform.domain.ForbiddenException;
import com.opinio.passform.domain.InvalidRequestException;
import com.opinio.passform.domain.NotFoundException;
import com.opinio.passform.llm.LlmUnavailableException;

// 도메인 예외의 HTTP 상태 매핑은 여기서만 한다(AGENTS 5절).
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(NotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException exception) {
		return error(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> conflict(ConflictException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(InvalidRequestException.class)
	public ResponseEntity<Map<String, Object>> invalid(InvalidRequestException exception) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", exception.getMessage());
		List<String> questionIds = exception.getQuestionIds();
		if (!questionIds.isEmpty()) {
			body.put("question_ids", questionIds);
		}
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	public ResponseEntity<Map<String, Object>> malformed(Exception exception) {
		return error(HttpStatus.BAD_REQUEST, "invalid_request");
	}

	// SPEC에 없는 응답이다. 외부 호출 실패를 되묻기나 0건으로 바꾸지 않고 실패로 드러낸다.
	@ExceptionHandler(LlmUnavailableException.class)
	public ResponseEntity<Map<String, Object>> llmUnavailable(LlmUnavailableException exception) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "llm_unavailable");
	}

	private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error) {
		return ResponseEntity.status(status).body(Map.of("error", error));
	}

}
