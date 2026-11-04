package com.opinio.passform.demo.domain;

public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("not_found");
	}

}
