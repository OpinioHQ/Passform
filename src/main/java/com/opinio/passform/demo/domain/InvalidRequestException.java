package com.opinio.passform.demo.domain;

import java.util.List;

public class InvalidRequestException extends RuntimeException {

	private final List<String> questionIds;

	public InvalidRequestException(String error) {
		this(error, List.of());
	}

	public InvalidRequestException(String error, List<String> questionIds) {
		super(error);
		this.questionIds = questionIds;
	}

	public List<String> getQuestionIds() {
		return questionIds;
	}

}
