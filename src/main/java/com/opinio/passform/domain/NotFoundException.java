package com.opinio.passform.domain;

public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("not_found");
	}

}
