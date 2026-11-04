package com.opinio.passform.domain.analysis;

// SPEC 5절 309행: RequestContext.match — all(모두 만족)·any(하나라도)·not(어느 것도 만족하지 않음)
public enum MatchMode {
	ALL, ANY, NOT
}
