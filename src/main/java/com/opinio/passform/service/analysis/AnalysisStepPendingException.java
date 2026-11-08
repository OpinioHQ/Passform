package com.opinio.passform.service.analysis;

// 되묻기를 통과했지만 아직 없는 단계다. 다른 순서나 빈 요약을 내지 않고 실패시킨다. E7(정렬)이 지운다.
public class AnalysisStepPendingException extends RuntimeException {

	public AnalysisStepPendingException(String step) {
		super("분석 단계 대기 중: " + step);
	}

}
