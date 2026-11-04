package com.opinio.passform.domain;

public class ConflictException extends RuntimeException {

	public ConflictException(String error) {
		super(error);
	}

}
