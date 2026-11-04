package com.opinio.passform.demo.domain;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("forbidden");
	}

}
