package com.opinio.passform.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.config.SecurityConfig;

@WebMvcTest(SnakeCaseJsonTest.EchoController.class)
@Import({ SecurityConfig.class, SnakeCaseJsonTest.EchoController.class })
class SnakeCaseJsonTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void API의_요청과_응답_JSON은_snake_case다() throws Exception {
		mockMvc.perform(post("/test/echo").with(user("u")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"response_count\":3,\"form_version\":1}"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"response_count\":3,\"form_version\":1}", JsonCompareMode.STRICT));
	}

	@RestController
	static class EchoController {

		@PostMapping("/test/echo")
		EchoBody echo(@RequestBody EchoBody body) {
			return body;
		}

	}

	record EchoBody(int responseCount, int formVersion) {
	}

}
