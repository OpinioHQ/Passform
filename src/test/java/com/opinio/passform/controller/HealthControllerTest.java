package com.opinio.passform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.opinio.passform.config.SecurityConfig;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void AC33_health는_인증_없이_200과_status_ok를_반환한다() throws Exception {
		mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"ok\"}", JsonCompareMode.STRICT));
	}

	@Test
	void AC33_health_외_경로는_비인증_접근을_401로_막는다() throws Exception {
		mockMvc.perform(get("/forms"))
			.andExpect(status().isUnauthorized());
	}

}
