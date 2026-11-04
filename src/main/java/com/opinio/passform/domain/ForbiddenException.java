package com.opinio.passform.domain;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("forbidden");
	}

}
