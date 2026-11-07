package com.opinio.passform.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.config.SecurityConfig;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(ApiExceptionHandlerTest.ThrowingController.class)
@Import({ SecurityConfig.class, ApiExceptionHandlerTest.ThrowingController.class })
class ApiExceptionHandlerTest {

	// 오류 코드-HTTP 상태 표. 코드를 더하면 여기에도 한 줄 더해야 첫 테스트가 통과한다.
	private static final Map<ErrorCode, HttpStatus> EXPECTED = Map.ofEntries(
			Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
			Map.entry(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN),
			Map.entry(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
			Map.entry(ErrorCode.SCHEMA_INVALID, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INVALID_PAYMENT_LINK, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INVALID_BRANCH, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INVALID_DISPLAY_MODE, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.CONSENT_TEMPLATE_REQUIRED, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.AI_SUMMARY_LOCKED, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INVALID_TRANSITION, HttpStatus.CONFLICT),
			Map.entry(ErrorCode.RESPONSE_LIMIT_REACHED, HttpStatus.CONFLICT),
			Map.entry(ErrorCode.FORM_CLOSED, HttpStatus.CONFLICT),
			Map.entry(ErrorCode.CONSENT_REQUIRED, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.REQUIRED_MISSING, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INVALID_INPUT_FORMAT_VALUE, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.INVALID_OPTION, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.CHARACTER_LIMIT_EXCEEDED, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.FORM_VERSION_CHANGED, HttpStatus.CONFLICT),
			Map.entry(ErrorCode.INVALID_CONTEXT, HttpStatus.BAD_REQUEST),
			Map.entry(ErrorCode.LLM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@EnumSource(ErrorCode.class)
	void 모든_오류_코드가_정해진_상태와_error_본문으로_나간다(ErrorCode code) throws Exception {
		assertThat(EXPECTED).containsKey(code);

		mockMvc.perform(get("/test/domain/" + code.name()).with(user("u")))
			.andExpect(status().is(EXPECTED.get(code).value()))
			.andExpect(content().json("{\"error\":\"" + code.code() + "\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 추가_필드는_error와_같은_높이로_나가고_error를_덮어쓰지_못한다() throws Exception {
		mockMvc.perform(get("/test/details").with(user("u")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"invalid_request\",\"question_ids\":[\"q1\"]}",
					JsonCompareMode.STRICT));
	}

	@Test
	void 예상_못_한_예외는_내부_내용을_응답에_노출하지_않는다() throws Exception {
		mockMvc.perform(get("/test/unexpected").with(user("u")))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("{\"error\":\"internal_error\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 보안_예외는_500으로_바꾸지_않는다() throws Exception {
		mockMvc.perform(get("/test/denied").with(user("u"))).andExpect(status().isForbidden());
	}

	@Test
	void 없는_경로는_not_found다() throws Exception {
		mockMvc.perform(get("/test/none").with(user("u")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void 허용하지_않는_메서드는_상태를_유지하고_invalid_request다() throws Exception {
		mockMvc.perform(delete("/test/unexpected").with(user("u")).with(csrf()))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void 깨진_JSON은_invalid_request다() throws Exception {
		mockMvc.perform(post("/test/valid").with(user("u")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"invalid_request\"}", JsonCompareMode.STRICT));
	}

	@Test
	void Bean_Validation_실패는_invalid_request다() throws Exception {
		mockMvc.perform(post("/test/valid").with(user("u")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"invalid_request\"}", JsonCompareMode.STRICT));
	}

	@RestController
	static class ThrowingController {

		@GetMapping("/test/domain/{code}")
		void domain(@PathVariable ErrorCode code) {
			throw new DomainException(code);
		}

		@GetMapping("/test/details")
		void details() {
			throw new DomainException(ErrorCode.INVALID_REQUEST,
					Map.of("question_ids", List.of("q1"), "error", "overwritten"));
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("secret-internal-detail");
		}

		@GetMapping("/test/denied")
		void denied() {
			throw new AccessDeniedException("denied");
		}

		@PostMapping("/test/valid")
		void valid(@Valid @RequestBody ValidRequest request) {
		}

	}

	record ValidRequest(@NotBlank String name) {
	}

}
