package com.opinio.passform.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.ResponseRepository;

// 응답 제출. 아직 구현하지 않았다. 이 메시지의 예외만 골든 실행기가 skip으로 본다.
@Service
public class SubmissionService {

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final Clock clock;

	public SubmissionService(FormRepository forms, FormVersionRepository versions, ResponseRepository responses,
			Clock clock) {
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.clock = clock;
	}

	public SubmissionResult submit(SubmitCommand command) {
		throw new UnsupportedOperationException("not implemented");
	}

}
