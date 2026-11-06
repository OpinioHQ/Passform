package com.opinio.passform.controller;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// JSON의 true, false만 받는다. 기본 변환은 "true"와 1도 true로 바꾸고 null과 생략을 구분하지 못한다.
// 거부하면 읽기 오류가 되어 advice가 invalid_request로 보낸다. 생략한 필드는 null이다.
public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {

	@Override
	public Boolean deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() == JsonToken.VALUE_TRUE) {
			return Boolean.TRUE;
		}
		if (parser.currentToken() == JsonToken.VALUE_FALSE) {
			return Boolean.FALSE;
		}
		return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
	}

	// 명시한 null
	@Override
	public Boolean getNullValue(DeserializationContext context) {
		return context.reportInputMismatch(Boolean.class, "boolean이 아니다");
	}

	// 생략
	@Override
	public Object getAbsentValue(DeserializationContext context) {
		return null;
	}

}
