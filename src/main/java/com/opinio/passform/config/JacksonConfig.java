package com.opinio.passform.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

// 요청 DTO의 boolean·정수 필드는 JSON의 boolean·정수만 받는다. 기본값은 "true", 1을 true로, 2.9를 2로 바꾼다.
// 거부하면 읽기 오류가 되어 advice가 invalid_request로 보낸다. @WebMvcTest는 이 설정을 자동으로 읽지 않으니 @Import한다.
@Configuration
public class JacksonConfig {

	@Bean
	public JsonMapperBuilderCustomizer strictScalarRequests() {
		return builder -> builder
			.withCoercionConfig(LogicalType.Boolean, boolean_ -> boolean_
				.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
			.withCoercionConfig(LogicalType.Integer, integer -> integer
				.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}

}
